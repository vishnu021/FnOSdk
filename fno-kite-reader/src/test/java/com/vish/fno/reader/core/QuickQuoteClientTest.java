package com.vish.fno.reader.core;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.LTPQuote;
import com.zerodhatech.models.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuickQuoteClientTest {

    private static final String OPT_N = "NIFTY26O2722500PE";
    private static final String OPT_S = "SENSEX26O2981000CE";
    private static final List<String> SYMBOLS = List.of(OPT_N, OPT_S, "NIFTY 50", "SENSEX");

    @Mock private KiteSession session;
    @Mock private KiteConnect kite;
    @Mock private InstrumentCache cache;

    private QuickQuoteClient client;

    @BeforeEach
    void setUp() {
        lenient().when(session.getKiteSdk()).thenReturn(kite);
        lenient().when(cache.getExchangeForSymbol(OPT_N)).thenReturn("NFO");
        lenient().when(cache.getExchangeForSymbol(OPT_S)).thenReturn("BFO");
        lenient().when(cache.getExchangeForSymbol("NIFTY 50")).thenReturn("NSE");
        lenient().when(cache.getExchangeForSymbol("SENSEX")).thenReturn("BSE");
        client = new QuickQuoteClient(session, cache, 300);
    }

    private static LTPQuote quote(double price) {
        LTPQuote q = new LTPQuote();
        q.lastPrice = price;
        return q;
    }

    private Map<String, LTPQuote> fullResponse() {
        Map<String, LTPQuote> m = new HashMap<>();
        m.put("NFO:" + OPT_N, quote(10.5));
        m.put("BFO:" + OPT_S, quote(20.5));
        m.put("NSE:NIFTY 50", quote(22500));
        m.put("BSE:SENSEX", quote(81000));
        return m;
    }

    @Test
    void buildsExchangePrefixedKeysForOptionsAndIndices() throws Throwable {
        when(kite.getLTP(any(String[].class))).thenReturn(fullResponse());

        Map<String, Double> result = client.getLtp(SYMBOLS);

        ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);
        verify(kite).getLTP(captor.capture());
        assertEquals(Set.of("NFO:" + OPT_N, "BFO:" + OPT_S, "NSE:NIFTY 50", "BSE:SENSEX"), Set.of(captor.getValue()));
        assertEquals(10.5, result.get(OPT_N));
        assertEquals(20.5, result.get(OPT_S));
        assertEquals(22500.0, result.get("NIFTY 50"));
        assertEquals(81000.0, result.get("SENSEX"));
    }

    @Test
    void symbolMissingFromResponseIsAbsentNotZero() throws Throwable {
        Map<String, LTPQuote> resp = fullResponse();
        resp.remove("BSE:SENSEX");
        when(kite.getLTP(any(String[].class))).thenReturn(resp);

        Map<String, Double> result = client.getLtp(SYMBOLS);

        assertEquals(3, result.size());
        assertFalse(result.containsKey("SENSEX"));
    }

    @Test
    void kiteExceptionReturnsEmptyMap() throws Throwable {
        when(kite.getLTP(any(String[].class))).thenThrow(new IOException("boom"));
        assertTrue(client.getLtp(SYMBOLS).isEmpty());
        when(kite.getLTP(any(String[].class))).thenThrow(new KiteException("bad", 500));
        assertTrue(client.getLtp(SYMBOLS).isEmpty());
    }

    @Test
    void lockHeldLongerThanTimeoutReturnsEmptyWithin2s() throws Throwable {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(kite.getLTP(any(String[].class))).thenAnswer(inv -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return fullResponse();
        });
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            pool.submit(() -> client.getLtp(SYMBOLS));
            assertTrue(entered.await(2, TimeUnit.SECONDS));

            long start = System.nanoTime();
            Map<String, Double> result = client.getLtp(SYMBOLS);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertTrue(result.isEmpty());
            assertTrue(elapsedMs < 2500, "elapsed " + elapsedMs);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void netPositionsMapsSymbolToNetQuantity() throws Throwable {
        Position p = new Position();
        p.tradingSymbol = OPT_N;
        p.netQuantity = 65;
        when(kite.getPositions()).thenReturn(Map.of("net", List.of(p), "day", List.of()));

        Optional<Map<String, Integer>> result = client.getNetPositionQuantities();

        assertTrue(result.isPresent());
        assertEquals(Map.of(OPT_N, 65), result.get());
    }

    private static Position row(String symbol, int qty) {
        Position p = new Position();
        p.tradingSymbol = symbol;
        p.netQuantity = qty;
        return p;
    }

    @Test
    void sameSymbolAcrossProductsIsSummed() throws Throwable {
        when(kite.getPositions()).thenReturn(Map.of("net", List.of(row(OPT_N, 65), row(OPT_N, 65))));
        assertEquals(Map.of(OPT_N, 130), client.getNetPositionQuantities().orElseThrow());
    }

    @Test
    void offsettingRowsNetToZero() throws Throwable {
        when(kite.getPositions()).thenReturn(Map.of("net", List.of(row(OPT_N, 65), row(OPT_N, -65))));
        assertEquals(Map.of(OPT_N, 0), client.getNetPositionQuantities().orElseThrow());
    }

    @Test
    void netPositionsFailureIsEmptyOptional() throws Throwable {
        when(kite.getPositions()).thenThrow(new IOException("down"));
        assertTrue(client.getNetPositionQuantities().isEmpty());
    }

    @Test
    void doesNotUseTheSharedApiLock() throws Throwable {
        when(kite.getLTP(any(String[].class))).thenReturn(fullResponse());
        ApiRateLimiter shared = new ApiRateLimiter();
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            pool.submit(() -> shared.executeWithLock(() -> {
                held.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }, "hold"));
            assertTrue(held.await(2, TimeUnit.SECONDS));

            assertEquals(4, client.getLtp(SYMBOLS).size());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
