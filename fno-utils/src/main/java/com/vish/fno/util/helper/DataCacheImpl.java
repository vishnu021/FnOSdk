package com.vish.fno.util.helper;

import com.vish.fno.model.Candle;
import com.vish.fno.model.SymbolData;
import com.vish.fno.util.TimeUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consolidated DataCache implementation that works with both production and backtest environments.
 *
 * This class extends AbstractDataCache (tick caching) and adds candlestick data caching.
 * It uses interfaces (CandlestickDataProvider and HolidayCalendar) to abstract the data source,
 * making it usable in both OrderManager (production) and BacktestRunner (backtesting).
 *
 * Usage in OrderManager:
 * - Inject CandlestickService (implements CandlestickDataProvider)
 * - Inject CalendarService (implements HolidayCalendar)
 *
 * Usage in BacktestRunner:
 * - Inject BacktestCandlestickService (implements CandlestickDataProvider)
 * - Inject CalendarService (implements HolidayCalendar)
 */
@Slf4j
public class DataCacheImpl extends AbstractDataCache {
    private final CandlestickDataProvider candlestickDataProvider;

    private final CandleStickCache minuteDataCache; // today's cache
    private final HistoricDataCache historicDataCache; // historical cache
    private final HolidayCalendar holidayCalendar;
    private final TimeSource timeSource;
    private volatile String lastIntradayCacheDate;
    // ConcurrentHashMap required for computeIfAbsent atomicity — Map interface lacks this guarantee
    @SuppressWarnings("PMD.LooseCoupling")
    private final ConcurrentHashMap<String, Object> symbolFetchLocks = new ConcurrentHashMap<>();

    public DataCacheImpl(CandlestickDataProvider candlestickDataProvider,
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

        if (!currentDate.equals(lastIntradayCacheDate)) {
            log.info("Date changed from {} to {} — clearing intraday cache", lastIntradayCacheDate, currentDate);
            minuteDataCache.clearAll();
            clearTickCache();
            symbolFetchLocks.clear();
            lastIntradayCacheDate = currentDate;
        }

        if (isDataAvailable(symbol)) {
            return;
        }

        // Per-symbol lock prevents 18+ virtual threads from all calling the Kite API
        // for the same uncached symbol. Only the first thread fetches; others wait and
        // then see the cached result via the double-check on isDataAvailable().
        Object lock = symbolFetchLocks.computeIfAbsent(symbol, k -> new Object());
        synchronized (lock) {
            if (isDataAvailable(symbol)) {
                return;
            }
            Optional<SymbolData> data = candlestickDataProvider.getEntireDayHistoryData(currentDate, symbol);
            data.ifPresent(d -> {
                minuteDataCache.clear(symbol);
                minuteDataCache.update(symbol, d.data());
            });
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
