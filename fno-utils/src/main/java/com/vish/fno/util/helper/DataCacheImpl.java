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
    private final String todaysDate;
    private final CandlestickDataProvider candlestickDataProvider;

    private final CandleStickCache minuteDataCache; // today's cache
    private final HistoricDataCache historicDataCache; // historical cache
    private final HolidayCalendar holidayCalendar;
    private final TimeProvider timeProvider;

    public DataCacheImpl(CandlestickDataProvider candlestickDataProvider,
                         HolidayCalendar holidayCalendar,
                         TimeProvider timeProvider) {
        this.candlestickDataProvider = candlestickDataProvider;
        this.holidayCalendar = holidayCalendar;
        this.timeProvider = timeProvider;
        this.minuteDataCache = new CandleStickCache();
        this.historicDataCache = new HistoricDataCache();
        this.todaysDate = timeProvider.getTodaysDateString();
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
        if (isDataAvailable(symbol)) {
            return;
        }

        Optional<SymbolData> data = candlestickDataProvider.getEntireDayHistoryData(todaysDate, symbol);
        data.ifPresent(d -> {
            minuteDataCache.clear(symbol);
            minuteDataCache.update(symbol, d.data());
        });
    }

    private boolean isDataAvailable(String symbol) {
        final Candle latestCandle = minuteDataCache.getLatestCandle(symbol);
        if (latestCandle == null) {
            return false;
        }
        int latestIndexOfTime = TimeUtils.getIndexOfTimeStamp(TimeUtils.getDateTimeForZonedDateString(latestCandle.time()));
        int currentIndexOfTime = timeProvider.currentTimeStampIndex();
        return latestIndexOfTime == currentIndexOfTime - 1;
    }

    private void updateHistoricCache(String date, String symbol) {
        if (historicDataCache.getData(date, symbol) == null || historicDataCache.getData(date, symbol).isEmpty()) {
            log.info("updating intraday cache for date: {}, symbol: {} ", date, symbol);
            Optional<SymbolData> candleStickData = candlestickDataProvider.getEntireDayHistoryData(date, symbol, "minute");
            candleStickData.ifPresent(d -> historicDataCache.update(date, symbol, d.data()));
        }
    }
}
