package com.vish.fno.util.candle.store;

import com.vish.fno.model.Candle;
import com.vish.fno.model.SymbolData;
import com.vish.fno.util.time.TimeSource;
import com.vish.fno.util.time.TimeUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Candlestick data cache implementation for both production and backtest environments.
 *
 * <p>Implements {@link CandleStore} only — tick storage is handled separately by
 * {@link TickStoreImpl}. This follows the Interface Segregation Principle: no consumer
 * needs both tick and candle operations, so the implementations are independent.
 *
 * <p>Uses interfaces ({@link CandlestickDataProvider} and {@link HolidayCalendar}) to abstract
 * the data source:
 * <ul>
 *   <li>Production: {@code CandlestickService} fetches live candles from Kite API</li>
 *   <li>Backtest: {@code BacktestCandlestickService} fetches from OrderManager API</li>
 * </ul>
 *
 * <p>Thread safety: per-symbol {@link ReentrantLock} prevents multiple virtual threads
 * from racing on the same API call. ReentrantLock (not synchronized) avoids pinning
 * virtual threads to carrier threads.
 */
@Slf4j
public class CandleStoreImpl implements CandleStore {

    private final CandlestickDataProvider candlestickDataProvider;
    private final CandleStickCache minuteDataCache; // today's cache
    private final HistoricDataCache historicDataCache; // historical cache
    private final HolidayCalendar holidayCalendar;
    private final TimeSource timeSource;
    private volatile String lastIntradayCacheDate;

    // Guards the date-boundary check-then-clear in updateIntradayCache(). Without this lock,
    // multiple virtual threads arriving simultaneously at market open (9:15) can all see
    // lastIntradayCacheDate as stale, all enter the if-block, and race to clear caches —
    // one thread could populate data that another immediately clears.
    private final ReentrantLock dateChangeLock = new ReentrantLock();

    // ConcurrentHashMap required for computeIfAbsent atomicity — Map interface lacks this guarantee
    // ReentrantLock instead of synchronized to avoid pinning virtual threads to carrier threads.
    @SuppressWarnings("PMD.LooseCoupling")
    private final ConcurrentHashMap<String, ReentrantLock> symbolFetchLocks = new ConcurrentHashMap<>();

    public CandleStoreImpl(CandlestickDataProvider candlestickDataProvider,
                           HolidayCalendar holidayCalendar,
                           TimeSource timeSource) {
        this.candlestickDataProvider = candlestickDataProvider;
        this.holidayCalendar = holidayCalendar;
        this.timeSource = timeSource;
        this.minuteDataCache = new CandleStickCache();
        this.historicDataCache = new HistoricDataCache();
    }

    @Override
    public List<Candle> updateAndGetMinuteData(String symbol) {
        updateIntradayCache(symbol);
        return minuteDataCache.get(symbol);
    }

    @Override
    public List<Candle> updateAndGetHistoryMinuteData(String date, String symbol) {
        updateHistoricCache(date, symbol);
        return historicDataCache.getData(date, symbol);
    }

    @Override
    public List<Candle> getNCandles(final String symbol, final Date date, final int n) {
        final List<Candle> todaysCandles = minuteDataCache.get(symbol);
        final List<Candle> allCandles = new ArrayList<>();
        int remainingCandles = n;
        Date currentDay = date;

        while (remainingCandles > 0) {
            final List<Candle> currentDayCandles = currentDay.equals(date)
                    ? todaysCandles
                    : updateAndGetHistoryMinuteData(TimeUtils.getStringDate(currentDay), symbol);

            int candlesToAdd = Math.min(remainingCandles, currentDayCandles.size());
            allCandles.addAll(0, currentDayCandles.subList(currentDayCandles.size() - candlesToAdd, currentDayCandles.size()));

            remainingCandles -= candlesToAdd;

            if (remainingCandles > 0) {
                currentDay = holidayCalendar.getPreviousNonHolidayDate(currentDay);
            }

            LocalDate localDate1 = convertToLocalDate(currentDay);
            LocalDate localDate2 = convertToLocalDate(date);
            long daysBetween = ChronoUnit.DAYS.between(localDate1, localDate2);
            if (daysBetween > 5) {
                break;
            }
        }

        return allCandles;
    }

    private static LocalDate convertToLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    @Override
    public List<Candle> getNCandles(String symbol, Date date, int n, List<Candle> todaysCandles) {
        return getNCandles(symbol, date, n);
    }

    private void updateIntradayCache(String symbol) {
        String currentDate = timeSource.getTodaysDateString();

        // Date-boundary transition: clear candle caches when the trading day changes.
        // Uses dateChangeLock to ensure exactly one thread performs the clear;
        // without this, multiple virtual threads at market open could race — one populates
        // data while another clears it. Double-check inside the lock for efficiency.
        if (!currentDate.equals(lastIntradayCacheDate)) {
            dateChangeLock.lock();
            try {
                if (!currentDate.equals(lastIntradayCacheDate)) {
                    log.info("Date changed from {} to {} — clearing candle caches", lastIntradayCacheDate, currentDate);
                    minuteDataCache.clearAll();
                    symbolFetchLocks.clear();
                    lastIntradayCacheDate = currentDate;
                }
            } finally {
                dateChangeLock.unlock();
            }
        }

        if (isDataAvailable(symbol)) {
            return;
        }

        // Per-symbol ReentrantLock prevents 18+ virtual threads from all calling the Kite API
        // for the same uncached symbol. Only the first thread fetches; others wait and
        // then see the cached result via the double-check on isDataAvailable().
        // ReentrantLock (not synchronized) so virtual threads can unmount during I/O waits.
        ReentrantLock lock = symbolFetchLocks.computeIfAbsent(symbol, k -> new ReentrantLock());
        lock.lock();
        try {
            if (isDataAvailable(symbol)) {
                return;
            }
            Optional<SymbolData> data = candlestickDataProvider.getEntireDayHistoryData(currentDate, symbol);
            data.ifPresent(d -> {
                minuteDataCache.clear(symbol);
                minuteDataCache.update(symbol, d.data());
            });
        } finally {
            lock.unlock();
        }
    }

    private boolean isDataAvailable(String symbol) {
        return minuteDataCache.getLatestCandle(symbol).flatMap(latestCandle ->
            TimeUtils.getDateTimeForZonedDateString(latestCandle.time()).map(dateTime -> {
                int latestIndexOfTime = TimeUtils.getIndexOfTimeStamp(dateTime);
                int currentIndexOfTime = timeSource.currentTimeStampIndex();
                return latestIndexOfTime == currentIndexOfTime - 1;
            })
        ).orElse(false);
    }

    private void updateHistoricCache(String date, String symbol) {
        if (historicDataCache.getData(date, symbol) == null || historicDataCache.getData(date, symbol).isEmpty()) {
            log.info("updating intraday cache for date: {}, symbol: {} ", date, symbol);
            Optional<SymbolData> candleStickData = candlestickDataProvider.getEntireDayHistoryData(date, symbol, "minute");
            candleStickData.ifPresent(d -> historicDataCache.update(date, symbol, d.data()));
        }
    }
}
