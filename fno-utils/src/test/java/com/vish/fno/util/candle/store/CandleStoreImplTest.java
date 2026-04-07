package com.vish.fno.util.candle.store;

import com.vish.fno.model.Candle;
import com.vish.fno.model.CandleMetaData;
import com.vish.fno.model.SymbolData;
import com.vish.fno.util.time.TimeSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.vish.fno.util.FnoConstants.TOTAL_TRADING_MINUTES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandleStoreImplTest {

    @Mock
    private CandlestickDataProvider dataProvider;
    @Mock
    private HolidayCalendar holidayCalendar;
    @Mock
    private TimeSource timeSource;

    private CandleStoreImpl candleStore;

    @BeforeEach
    void setUp() {
        candleStore = new CandleStoreImpl(dataProvider, holidayCalendar, timeSource);
    }

    // ── Historic cache: basic fetch and caching ──────────────────────────────

    @Test
    void updateAndGetHistoryMinuteData_fetchesAndCachesFullDayData() {
        List<Candle> candles = createCandles(TOTAL_TRADING_MINUTES);
        SymbolData symbolData = createSymbolData("2026-04-01", "NIFTY 50", candles);
        when(dataProvider.getEntireDayHistoryData("2026-04-01", "NIFTY 50", "minute"))
                .thenReturn(Optional.of(symbolData));

        List<Candle> result = candleStore.updateAndGetHistoryMinuteData("2026-04-01", "NIFTY 50");

        assertEquals(TOTAL_TRADING_MINUTES, result.size());
        verify(dataProvider, times(1)).getEntireDayHistoryData("2026-04-01", "NIFTY 50", "minute");
    }

    @Test
    void updateAndGetHistoryMinuteData_skipsRefetchWhenDataComplete() {
        List<Candle> candles = createCandles(TOTAL_TRADING_MINUTES);
        SymbolData symbolData = createSymbolData("2026-04-01", "NIFTY 50", candles);
        when(dataProvider.getEntireDayHistoryData("2026-04-01", "NIFTY 50", "minute"))
                .thenReturn(Optional.of(symbolData));

        candleStore.updateAndGetHistoryMinuteData("2026-04-01", "NIFTY 50");
        candleStore.updateAndGetHistoryMinuteData("2026-04-01", "NIFTY 50");

        verify(dataProvider, times(1)).getEntireDayHistoryData("2026-04-01", "NIFTY 50", "minute");
    }

    @Test
    void updateAndGetHistoryMinuteData_refetchesWhenDataIncomplete() {
        List<Candle> partialCandles = createCandles(100);
        SymbolData partialData = createSymbolData("2026-04-01", "NIFTY 50", partialCandles);
        when(dataProvider.getEntireDayHistoryData("2026-04-01", "NIFTY 50", "minute"))
                .thenReturn(Optional.of(partialData));

        candleStore.updateAndGetHistoryMinuteData("2026-04-01", "NIFTY 50");
        candleStore.updateAndGetHistoryMinuteData("2026-04-01", "NIFTY 50");

        // Data was incomplete (100 < 375), so second call triggers another fetch
        verify(dataProvider, times(2)).getEntireDayHistoryData("2026-04-01", "NIFTY 50", "minute");
    }

    @Test
    void updateAndGetHistoryMinuteData_returnsEmptyListWhenNoData() {
        when(dataProvider.getEntireDayHistoryData("2026-04-01", "NIFTY 50", "minute"))
                .thenReturn(Optional.empty());

        List<Candle> result = candleStore.updateAndGetHistoryMinuteData("2026-04-01", "NIFTY 50");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void updateAndGetHistoryMinuteData_differentSymbolsSameDateFetchSeparately() {
        List<Candle> candles = createCandles(TOTAL_TRADING_MINUTES);
        when(dataProvider.getEntireDayHistoryData(eq("2026-04-01"), anyString(), eq("minute")))
                .thenReturn(Optional.of(createSymbolData("2026-04-01", "NIFTY 50", candles)));

        candleStore.updateAndGetHistoryMinuteData("2026-04-01", "NIFTY 50");
        candleStore.updateAndGetHistoryMinuteData("2026-04-01", "BANKNIFTY");

        verify(dataProvider).getEntireDayHistoryData("2026-04-01", "NIFTY 50", "minute");
        verify(dataProvider).getEntireDayHistoryData("2026-04-01", "BANKNIFTY", "minute");
    }

    @Test
    void updateAndGetHistoryMinuteData_differentDatesSameSymbolFetchSeparately() {
        List<Candle> candles = createCandles(TOTAL_TRADING_MINUTES);
        when(dataProvider.getEntireDayHistoryData(anyString(), eq("NIFTY 50"), eq("minute")))
                .thenReturn(Optional.of(createSymbolData("2026-04-01", "NIFTY 50", candles)));

        candleStore.updateAndGetHistoryMinuteData("2026-04-01", "NIFTY 50");
        candleStore.updateAndGetHistoryMinuteData("2026-04-02", "NIFTY 50");

        verify(dataProvider).getEntireDayHistoryData("2026-04-01", "NIFTY 50", "minute");
        verify(dataProvider).getEntireDayHistoryData("2026-04-02", "NIFTY 50", "minute");
    }

    // ── Historic cache: concurrent access ────────────────────────────────────

    @Test
    void updateAndGetHistoryMinuteData_concurrentCallsOnlyFetchOnce() throws InterruptedException {
        List<Candle> candles = createCandles(TOTAL_TRADING_MINUTES);
        SymbolData symbolData = createSymbolData("2026-04-01", "NIFTY 50", candles);
        AtomicInteger fetchCount = new AtomicInteger(0);

        when(dataProvider.getEntireDayHistoryData("2026-04-01", "NIFTY 50", "minute"))
                .thenAnswer(invocation -> {
                    fetchCount.incrementAndGet();
                    // Simulate API latency to widen the race window
                    Thread.sleep(50);
                    return Optional.of(symbolData);
                });

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    candleStore.updateAndGetHistoryMinuteData("2026-04-01", "NIFTY 50");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // Double-checked locking should ensure only 1 actual fetch
        assertEquals(1, fetchCount.get());
    }

    // ── Lock separation: intraday clear doesn't affect historic ──────────────

    @Test
    void dateChange_clearsIntradayButNotHistoricCache() {
        // Pre-populate historic cache
        List<Candle> historicCandles = createCandles(TOTAL_TRADING_MINUTES);
        when(dataProvider.getEntireDayHistoryData("2026-04-01", "NIFTY 50", "minute"))
                .thenReturn(Optional.of(createSymbolData("2026-04-01", "NIFTY 50", historicCandles)));
        candleStore.updateAndGetHistoryMinuteData("2026-04-01", "NIFTY 50");

        // Simulate date change triggering intraday cache clear
        when(timeSource.getTodaysDateString()).thenReturn("2026-04-01", "2026-04-02");

        // First intraday call sets lastIntradayCacheDate to "2026-04-01"
        candleStore.updateAndGetMinuteData("NIFTY 50");
        // Second call with new date triggers date change → clears intraday caches
        candleStore.updateAndGetMinuteData("NIFTY 50");

        // Historic cache should still be intact — no re-fetch needed
        candleStore.updateAndGetHistoryMinuteData("2026-04-01", "NIFTY 50");
        verify(dataProvider, times(1)).getEntireDayHistoryData("2026-04-01", "NIFTY 50", "minute");
    }

    // ── Intraday: date boundary clears cache ─────────────────────────────────

    @Test
    void updateAndGetMinuteData_clearsOnDateChange() {
        when(timeSource.getTodaysDateString()).thenReturn("2026-04-01", "2026-04-02");

        // First call — sets date to 2026-04-01
        candleStore.updateAndGetMinuteData("NIFTY 50");
        // Second call — date changes to 2026-04-02, triggers clear
        candleStore.updateAndGetMinuteData("NIFTY 50");

        // Both calls should fetch since cache was cleared between them
        verify(dataProvider, times(2)).getEntireDayHistoryData(anyString(), eq("NIFTY 50"));
    }

    @Test
    void updateAndGetMinuteData_sameDateDoesNotClearCache() {
        when(timeSource.getTodaysDateString()).thenReturn("2026-04-01");

        candleStore.updateAndGetMinuteData("NIFTY 50");
        candleStore.updateAndGetMinuteData("NIFTY 50");

        // isDataAvailable returns false both times (no candle data from mock),
        // but no date change → no cache clear logged
        verify(dataProvider, times(2)).getEntireDayHistoryData("2026-04-01", "NIFTY 50");
    }

    // ── Intraday: skips fetch when data is current ───────────────────────────

    @Test
    void updateAndGetMinuteData_skipsFetchWhenDataIsCurrent() {
        when(timeSource.getTodaysDateString()).thenReturn("2026-04-01");
        when(timeSource.currentTimeStampIndex()).thenReturn(2);

        // First call fetches and populates cache with a candle at index 1 (current - 1)
        Candle candle = new Candle("2026-04-01T09:16:00+0530", 100.0, 101.0, 99.0, 100.5, 1000L, 500L);
        SymbolData symbolData = createSymbolData("2026-04-01", "NIFTY 50", List.of(candle));
        when(dataProvider.getEntireDayHistoryData("2026-04-01", "NIFTY 50"))
                .thenReturn(Optional.of(symbolData));

        candleStore.updateAndGetMinuteData("NIFTY 50");
        candleStore.updateAndGetMinuteData("NIFTY 50");

        // Second call should skip fetch because isDataAvailable returns true
        verify(dataProvider, times(1)).getEntireDayHistoryData("2026-04-01", "NIFTY 50");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static List<Candle> createCandles(int count) {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int hour = 9 + (15 + i) / 60;
            int minute = (15 + i) % 60;
            String time = String.format("2026-04-01T%02d:%02d:00+0530", hour, minute);
            candles.add(new Candle(time, 100.0 + i, 101.0 + i, 99.0 + i, 100.5 + i, 1000L, 500L));
        }
        return candles;
    }

    private static SymbolData createSymbolData(String date, String symbol, List<Candle> candles) {
        return new SymbolData(new CandleMetaData(date, symbol), candles);
    }
}
