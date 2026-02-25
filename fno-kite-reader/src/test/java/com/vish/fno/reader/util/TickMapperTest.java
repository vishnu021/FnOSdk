package com.vish.fno.reader.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vish.fno.model.Ticker;
import com.zerodhatech.models.Depth;
import com.zerodhatech.models.Tick;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Slf4j
class TickMapperTest {

    private static final String TICK_JSON = """
        {
            "mode": "full",
            "instrumentToken": 13259778,
            "lastTradedPrice": 153.9,
            "change": -43.53329664281783,
            "lastTradedQuantity": 75.0,
            "averageTradePrice": 153.63,
            "volumeTradedToday": 1421325,
            "totalBuyQuantity": 299575.0,
            "totalSellQuantity": 74925.0,
            "lastTradedTime": 1722570360000,
            "oi": 1077275.0,
            "openInterestDayHigh": 1077275.0,
            "openInterestDayLow": 752125.0,
            "tickTimestamp": 1722570360000,
            "marketDepth": {
                "buy": [
                    {"quantity": 75, "price": 153.6, "orders": 2},
                    {"quantity": 25, "price": 153.55, "orders": 1},
                    {"quantity": 850, "price": 153.5, "orders": 4},
                    {"quantity": 450, "price": 153.45, "orders": 5},
                    {"quantity": 600, "price": 153.4, "orders": 6}
                ],
                "sell": [
                    {"quantity": 150, "price": 153.9, "orders": 1},
                    {"quantity": 300, "price": 153.95, "orders": 2},
                    {"quantity": 500, "price": 154.0, "orders": 1},
                    {"quantity": 450, "price": 154.05, "orders": 2},
                    {"quantity": 1000, "price": 154.1, "orders": 5}
                ]
            }
        }
        """;

    @Test
    void testMapTicks_withJsonTick() throws Exception {
        // Given
        ObjectMapper objectMapper = new ObjectMapper();
        Tick tick = objectMapper.readValue(TICK_JSON, Tick.class);

        List<Tick> tickList = List.of(tick);

        // When
        Ticker ticker = TickMapper.mapTick(tick, "");

        // Then
        assertEquals(153.9, ticker.lastTradedPrice());
        assertEquals(13259778, ticker.instrumentToken());
        assertEquals(-43.53329664281783, ticker.change());

        // Assert Market Depth (buy)
        assertNotNull(ticker.depth());
        List<Ticker.Depth> buyDepth = ticker.depth().get("buy");
        assertEquals(5, buyDepth.size());

        assertEquals(75, buyDepth.get(0).quantity());
        assertEquals(153.6, buyDepth.get(0).price());
        assertEquals(2, buyDepth.get(0).orders());

        assertEquals(600, buyDepth.get(4).quantity());
        assertEquals(153.4, buyDepth.get(4).price());
        assertEquals(6, buyDepth.get(4).orders());

        // Assert Market Depth (sell)
        List<Ticker.Depth> sellDepth = ticker.depth().get("sell");
        assertEquals(5, sellDepth.size());

        assertEquals(150, sellDepth.get(0).quantity());
        assertEquals(153.9, sellDepth.get(0).price());
        assertEquals(1, sellDepth.get(0).orders());

        assertEquals(1000, sellDepth.get(4).quantity());
        assertEquals(154.1, sellDepth.get(4).price());
        assertEquals(5, sellDepth.get(4).orders());

        // Assert tickReceivedTime is captured at mapping time
        assertNotNull(ticker.tickReceivedTime(), "tickReceivedTime should be populated at mapping time");
    }

    @Test
    void testMapTick_withoutDepth() throws Exception {
        // Given
        ObjectMapper objectMapper = new ObjectMapper();
        Tick tick = objectMapper.readValue(TICK_JSON, Tick.class);
        String symbol = "NIFTY24AUG24500CE";

        // When
        Ticker ticker = TickMapper.mapTick(tick, symbol, false);

        // Then
        assertNull(ticker.depth(), "depth should be null when includeDepth is false");
        assertEquals(153.9, ticker.lastTradedPrice());
        assertEquals(13259778, ticker.instrumentToken());
        assertEquals(-43.53329664281783, ticker.change());
        assertEquals(symbol, ticker.instrumentSymbol());
        assertNotNull(ticker.tickReceivedTime());
    }

    @Test
    void testMapTick_withNullMarketDepth() {
        // Given
        Tick tick = new Tick();
        tick.setMode("full");
        tick.setInstrumentToken(12345L);
        tick.setLastTradedPrice(100.5);
        tick.setMarketDepth(null);

        // When
        Ticker ticker = TickMapper.mapTick(tick, "TESTSTOCK", true);

        // Then
        assertNull(ticker.depth(), "depth should be null when marketDepth is null");
        assertEquals(100.5, ticker.lastTradedPrice());
        assertEquals(12345L, ticker.instrumentToken());
    }

    @Test
    void testMapTick_symbolPreserved() throws Exception {
        // Given
        ObjectMapper objectMapper = new ObjectMapper();
        Tick tick = objectMapper.readValue(TICK_JSON, Tick.class);
        String expectedSymbol = "BANKNIFTY24AUG51000PE";

        // When
        Ticker ticker = TickMapper.mapTick(tick, expectedSymbol, false);

        // Then
        assertEquals(expectedSymbol, ticker.instrumentSymbol(),
                "tickSymbol parameter should be used as instrumentSymbol in the output");
    }

    @Test
    void testMapTick_withEmptyMarketDepth() {
        // Given
        Tick tick = new Tick();
        tick.setMode("full");
        tick.setInstrumentToken(67890L);
        tick.setLastTradedPrice(200.0);
        Map<String, ArrayList<Depth>> emptyDepth = new HashMap<>();
        tick.setMarketDepth(emptyDepth);

        // When
        Ticker ticker = TickMapper.mapTick(tick, "EMPTYSTOCK", true);

        // Then
        assertNull(ticker.depth(), "depth should be null when marketDepth map is empty");
        assertEquals(200.0, ticker.lastTradedPrice());
    }
}
