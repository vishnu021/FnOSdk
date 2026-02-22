package com.vish.fno.reader.core;

import com.vish.fno.reader.model.InstrumentSummary;
import com.vish.fno.reader.model.KiteOpenOrder;
import com.vish.fno.reader.util.OptionPriceUtils;
import com.zerodhatech.models.HistoricalData;
import com.zerodhatech.models.Instrument;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.Position;
import com.zerodhatech.ticker.OnOrderUpdate;
import com.zerodhatech.ticker.OnTicks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("PMD.TooManyStaticImports")
class KiteServiceTest {

    private static final String SYMBOL = "NIFTY2610626000CE";
    private static final String INDEX_SYMBOL = "NIFTY 50";
    private static final String TAG = "testTag";
    private static final int ORDER_SIZE = 50;

    @Mock
    private KiteSession session;
    @Mock
    private InstrumentCache instrumentCache;
    @Mock
    private HistoricalDataProvider dataProvider;
    @Mock
    private KiteOrderExecutor orderExecutor;
    @Mock
    private KiteWebSocket kiteWebSocket;

    private KiteService kiteService;

    @BeforeEach
    void setUp() throws Exception {
        kiteService = new KiteService("secret", "apiKey", "userId", List.of(), false, false);
        setField("session", session);
        setField("instrumentCache", instrumentCache);
        setField("dataProvider", dataProvider);
        setField("orderExecutor", orderExecutor);
        setField("kiteWebSocket", kiteWebSocket);
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = KiteService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(kiteService, value);
    }

    // =======================================================================
    // Authentication
    // =======================================================================

    @Test
    void testIsInitialised_delegatesToSession() {
        // Arrange
        when(session.isInitialised()).thenReturn(true);

        // Act
        boolean result = kiteService.isInitialised();

        // Assert
        assertTrue(result);
        verify(session).isInitialised();
    }

    @Test
    void testIsInitialised_returnsFalseWhenNotInitialised() {
        // Arrange
        when(session.isInitialised()).thenReturn(false);

        // Act
        boolean result = kiteService.isInitialised();

        // Assert
        assertFalse(result);
        verify(session).isInitialised();
    }

    // =======================================================================
    // Historical data
    // =======================================================================

    @Test
    void testGetEntireDayHistoricalData_delegatesToDataProvider() {
        // Arrange
        Date from = new Date();
        Date to = new Date();
        String interval = "minute";
        HistoricalData expectedData = new HistoricalData();
        when(dataProvider.getEntireDayHistoricalData(from, to, SYMBOL, interval))
                .thenReturn(Optional.of(expectedData));

        // Act
        Optional<HistoricalData> result = kiteService.getEntireDayHistoricalData(from, to, SYMBOL, interval);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(expectedData, result.get());
        verify(dataProvider).getEntireDayHistoricalData(from, to, SYMBOL, interval);
    }

    @Test
    void testGetHistoricalData_delegatesToDataProvider() {
        // Arrange
        Date from = new Date();
        Date to = new Date();
        String interval = "day";
        HistoricalData expectedData = new HistoricalData();
        when(dataProvider.getHistoricalData(from, to, SYMBOL, interval, true))
                .thenReturn(Optional.of(expectedData));

        // Act
        Optional<HistoricalData> result = kiteService.getHistoricalData(from, to, SYMBOL, interval, true);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(expectedData, result.get());
        verify(dataProvider).getHistoricalData(from, to, SYMBOL, interval, true);
    }

    // =======================================================================
    // Option methods (static delegation via OptionPriceUtils)
    // =======================================================================

    @Test
    void testGetITMStock_delegatesToOptionPriceUtils() {
        // Arrange
        List<Instrument> instruments = List.of();
        when(instrumentCache.getInstruments()).thenReturn(instruments);

        try (MockedStatic<OptionPriceUtils> mockedStatic = Mockito.mockStatic(OptionPriceUtils.class)) {
            mockedStatic.when(() -> OptionPriceUtils.getITMStock(INDEX_SYMBOL, 20000.0, true, instruments))
                    .thenReturn("NIFTY26FEB20000CE");

            // Act
            String result = kiteService.getITMStock(INDEX_SYMBOL, 20000.0, true);

            // Assert
            assertEquals("NIFTY26FEB20000CE", result);
            verify(instrumentCache).getInstruments();
            mockedStatic.verify(() -> OptionPriceUtils.getITMStock(INDEX_SYMBOL, 20000.0, true, instruments));
        }
    }

    @Test
    void testGetOTMStock_delegatesToOptionPriceUtils() {
        // Arrange
        List<Instrument> instruments = List.of();
        when(instrumentCache.getInstruments()).thenReturn(instruments);

        try (MockedStatic<OptionPriceUtils> mockedStatic = Mockito.mockStatic(OptionPriceUtils.class)) {
            mockedStatic.when(() -> OptionPriceUtils.getOTMStock(INDEX_SYMBOL, 20000.0, false, instruments))
                    .thenReturn("NIFTY26FEB19800PE");

            // Act
            String result = kiteService.getOTMStock(INDEX_SYMBOL, 20000.0, false);

            // Assert
            assertEquals("NIFTY26FEB19800PE", result);
            verify(instrumentCache).getInstruments();
            mockedStatic.verify(() -> OptionPriceUtils.getOTMStock(INDEX_SYMBOL, 20000.0, false, instruments));
        }
    }

    // =======================================================================
    // WebSocket listener methods
    // =======================================================================

    @Test
    void testSetOnTickerArrivalListener_delegatesToWebSocket() {
        // Arrange
        OnTicks listener = ticks -> { };

        // Act
        kiteService.setOnTickerArrivalListener(listener);

        // Assert
        verify(kiteWebSocket).setOnTickerArrivalListener(listener);
    }

    @Test
    void testSetOnTickerArrivalListener_nullReturnsEarly() {
        // Act
        kiteService.setOnTickerArrivalListener(null);

        // Assert
        verify(kiteWebSocket, never()).setOnTickerArrivalListener(any());
    }

    @Test
    void testSetOnOrderUpdateListener_delegatesToWebSocket() {
        // Arrange
        OnOrderUpdate listener = order -> { };

        // Act
        kiteService.setOnOrderUpdateListener(listener);

        // Assert
        verify(kiteWebSocket).setOnOrderUpdateListener(listener);
    }

    @Test
    void testSetOnOrderUpdateListener_nullReturnsEarly() {
        // Act
        kiteService.setOnOrderUpdateListener(null);

        // Assert
        verify(kiteWebSocket, never()).setOnOrderUpdateListener(any());
    }

    // =======================================================================
    // WebSocket subscription methods
    // =======================================================================

    @Test
    void testAppendWebSocketSymbolsList_delegatesToWebSocket() {
        // Arrange
        List<String> symbols = List.of("NIFTY26FEB20000CE", "NIFTY26FEB20000PE");

        // Act
        kiteService.appendWebSocketSymbolsList(symbols, true);

        // Assert
        verify(kiteWebSocket).appendWebSocketSymbolsList(symbols, true);
    }

    @Test
    void testGetSubscribedWebSocketTokens_delegatesToWebSocket() {
        // Arrange
        List<Long> expectedTokens = List.of(256265L, 260105L);
        when(kiteWebSocket.getSubscribedTokens()).thenReturn(expectedTokens);

        // Act
        List<Long> result = kiteService.getSubscribedWebSocketTokens();

        // Assert
        assertEquals(expectedTokens, result);
        verify(kiteWebSocket).getSubscribedTokens();
    }

    @Test
    void testGetSubscribedWebSocketTokensCount_delegatesToWebSocket() {
        // Arrange
        when(kiteWebSocket.getSubscribedTokensCount()).thenReturn(42);

        // Act
        int result = kiteService.getSubscribedWebSocketTokensCount();

        // Assert
        assertEquals(42, result);
        verify(kiteWebSocket).getSubscribedTokensCount();
    }

    @Test
    void testIsSymbolSubscribed_delegatesToWebSocket() {
        // Arrange
        when(kiteWebSocket.isSymbolSubscribed(SYMBOL)).thenReturn(true);

        // Act
        boolean result = kiteService.isSymbolSubscribed(SYMBOL);

        // Assert
        assertTrue(result);
        verify(kiteWebSocket).isSymbolSubscribed(SYMBOL);
    }

    // =======================================================================
    // Instrument cache methods
    // =======================================================================

    @Test
    void testGetInstrument_delegatesToInstrumentCache() {
        // Arrange
        when(instrumentCache.getInstrument(SYMBOL)).thenReturn(Optional.of(12345L));

        // Act
        Optional<Long> result = kiteService.getInstrument(SYMBOL);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(12345L, result.get());
        verify(instrumentCache).getInstrument(SYMBOL);
    }

    @Test
    void testGetSymbol_delegatesToInstrumentCache() {
        // Arrange
        when(instrumentCache.getSymbol(256265L)).thenReturn("NIFTY 50");

        // Act
        String result = kiteService.getSymbol(256265L);

        // Assert
        assertEquals("NIFTY 50", result);
        verify(instrumentCache).getSymbol(256265L);
    }

    @Test
    void testGetFilteredInstruments_delegatesToGetAllInstruments() {
        // Arrange
        List<InstrumentSummary> expectedSummaries = List.of(
                new InstrumentSummary("NFO", SYMBOL, "2026-02-27")
        );
        when(instrumentCache.getAllInstruments()).thenReturn(expectedSummaries);

        // Act
        List<InstrumentSummary> result = kiteService.getFilteredInstruments();

        // Assert
        assertEquals(expectedSummaries, result);
        verify(instrumentCache).getAllInstruments();
    }

    @Test
    void testIsExpiryDayForOption_delegatesToInstrumentCache() {
        // Arrange
        Date date = new Date();
        when(instrumentCache.isExpiryDayForOption(SYMBOL, date)).thenReturn(true);

        // Act
        boolean result = kiteService.isExpiryDayForOption(SYMBOL, date);

        // Assert
        assertTrue(result);
        verify(instrumentCache).isExpiryDayForOption(SYMBOL, date);
    }

    @Test
    void testGetInstruments_delegatesToInstrumentCache() {
        // Arrange
        List<Instrument> expectedInstruments = List.of(new Instrument());
        when(instrumentCache.getInstruments()).thenReturn(expectedInstruments);

        // Act
        List<Instrument> result = kiteService.getInstruments();

        // Assert
        assertEquals(expectedInstruments, result);
        verify(instrumentCache).getInstruments();
    }

    @Test
    void testGetInstrumentCacheSize_delegatesToInstrumentMapSize() {
        // Arrange
        when(instrumentCache.getInstrumentMapSize()).thenReturn(500);

        // Act
        int result = kiteService.getInstrumentCacheSize();

        // Assert
        assertEquals(500, result);
        verify(instrumentCache).getInstrumentMapSize();
    }

    @Test
    void testGetLotSizeFromFuture_delegatesToInstrumentCache() {
        // Arrange
        when(instrumentCache.getLotSizeFromFuture(INDEX_SYMBOL)).thenReturn(Optional.of(50));

        // Act
        Optional<Integer> result = kiteService.getLotSizeFromFuture(INDEX_SYMBOL);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(50, result.get());
        verify(instrumentCache).getLotSizeFromFuture(INDEX_SYMBOL);
    }

    @Test
    void testGetAllFutureLotSizeInfo_delegatesToInstrumentCache() {
        // Arrange
        Map<String, Integer> expectedMap = Map.of("NIFTY 50", 50, "NIFTY BANK", 15);
        when(instrumentCache.getAllFutureLotSizeInfo()).thenReturn(expectedMap);

        // Act
        Map<String, Integer> result = kiteService.getAllFutureLotSizeInfo();

        // Assert
        assertEquals(expectedMap, result);
        verify(instrumentCache).getAllFutureLotSizeInfo();
    }

    // =======================================================================
    // Order methods
    // =======================================================================

    @Test
    void testPlaceOptionOrder_delegatesToOrderExecutor() {
        // Arrange
        OrderParams orderParams = new OrderParams();
        orderParams.tradingsymbol = SYMBOL;
        Order expectedOrder = new Order();
        expectedOrder.orderId = "OPT123";
        when(orderExecutor.placeOptionOrder(orderParams)).thenReturn(expectedOrder);

        // Act
        Order result = kiteService.placeOptionOrder(orderParams);

        // Assert
        assertNotNull(result);
        assertEquals("OPT123", result.orderId);
        verify(orderExecutor).placeOptionOrder(orderParams);
    }

    @Test
    void testBuyOrder_delegatesToOrderExecutor() {
        // Arrange
        KiteOpenOrder expectedOrder = new KiteOpenOrder(new Order(), true, null, null);
        when(orderExecutor.buyOrder(SYMBOL, ORDER_SIZE, TAG, true))
                .thenReturn(Optional.of(expectedOrder));

        // Act
        Optional<KiteOpenOrder> result = kiteService.buyOrder(SYMBOL, ORDER_SIZE, TAG, true);

        // Assert
        assertTrue(result.isPresent());
        assertTrue(result.get().isOrderPlaced());
        verify(orderExecutor).buyOrder(SYMBOL, ORDER_SIZE, TAG, true);
    }

    @Test
    void testSellOrder_delegatesToOrderExecutor() {
        // Arrange
        KiteOpenOrder expectedOrder = new KiteOpenOrder(new Order(), true, null, null);
        when(orderExecutor.sellOrder(SYMBOL, ORDER_SIZE, TAG, true))
                .thenReturn(Optional.of(expectedOrder));

        // Act
        Optional<KiteOpenOrder> result = kiteService.sellOrder(SYMBOL, ORDER_SIZE, TAG, true);

        // Assert
        assertTrue(result.isPresent());
        assertTrue(result.get().isOrderPlaced());
        verify(orderExecutor).sellOrder(SYMBOL, ORDER_SIZE, TAG, true);
    }

    @Test
    void testGetOrders_delegatesToOrderExecutor() {
        // Arrange
        Order order = new Order();
        order.orderId = "ORD001";
        List<Order> expectedOrders = List.of(order);
        when(orderExecutor.getOrders()).thenReturn(expectedOrders);

        // Act
        List<Order> result = kiteService.getOrders();

        // Assert
        assertEquals(1, result.size());
        assertEquals("ORD001", result.get(0).orderId);
        verify(orderExecutor).getOrders();
    }

    @Test
    void testGetPositions_delegatesToOrderExecutor() {
        // Arrange
        Position position = new Position();
        position.tradingSymbol = SYMBOL;
        Map<String, List<Position>> expectedPositions = Map.of(
                "net", List.of(position),
                "day", List.of()
        );
        when(orderExecutor.getPositions()).thenReturn(expectedPositions);

        // Act
        Map<String, List<Position>> result = kiteService.getPositions();

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.containsKey("net"));
        verify(orderExecutor).getPositions();
    }

    // =======================================================================
    // getAllOptionSymbols
    // =======================================================================

    @Test
    void testGetAllOptionSymbols_successCase() {
        // Arrange
        List<Instrument> instruments = List.of();
        when(instrumentCache.getInstruments()).thenReturn(instruments);
        List<String> expectedSymbols = List.of("NIFTY26FEB20000CE", "NIFTY26FEB20000PE");

        try (MockedStatic<OptionPriceUtils> mockedStatic = Mockito.mockStatic(OptionPriceUtils.class)) {
            mockedStatic.when(() -> OptionPriceUtils.getAllOptionSymbols(INDEX_SYMBOL, instruments))
                    .thenReturn(expectedSymbols);

            // Act
            List<String> result = kiteService.getAllOptionSymbols(INDEX_SYMBOL);

            // Assert
            assertEquals(2, result.size());
            assertEquals(expectedSymbols, result);
        }
    }

    @Test
    void testGetAllOptionSymbols_exceptionReturnsEmptyList() {
        // Arrange
        when(instrumentCache.getInstruments()).thenThrow(new RuntimeException("Cache not ready"));

        // Act
        List<String> result = kiteService.getAllOptionSymbols(INDEX_SYMBOL);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // =======================================================================
    // appendIndexITMOptions
    // =======================================================================

    @Test
    void testAppendIndexITMOptions_notConnectedToWebSocket() throws Exception {
        // Arrange
        when(kiteWebSocket.isConnectToWebSocket()).thenReturn(false);

        // Act
        kiteService.appendIndexITMOptions();

        // Assert - should not try to append symbols when not connected
        verify(kiteWebSocket, never()).appendWebSocketSymbolsList(any(), anyBoolean());
    }

    @Test
    void testAppendIndexITMOptions_alreadyAppended() throws Exception {
        // Arrange - set itmOptionsAppended to true via reflection
        Field itmField = KiteService.class.getDeclaredField("itmOptionsAppended");
        itmField.setAccessible(true);
        itmField.set(kiteService, true);
        when(kiteWebSocket.isConnectToWebSocket()).thenReturn(true);

        // Act
        kiteService.appendIndexITMOptions();

        // Assert - should not fetch data when already appended
        verifyNoInteractions(dataProvider);
    }

    // =======================================================================
    // appendAllOptionsForIndex
    // =======================================================================

    @Test
    void testAppendAllOptionsForIndex_notConnectedToWebSocket() {
        // Arrange
        when(kiteWebSocket.isConnectToWebSocket()).thenReturn(false);

        // Act
        kiteService.appendAllOptionsForIndex(INDEX_SYMBOL);

        // Assert - should not interact with instrumentCache or kiteWebSocket for subscriptions
        verify(kiteWebSocket, never()).appendWebSocketSymbolsList(any(), anyBoolean());
    }

    @Test
    void testAppendAllOptionsForIndex_connectedDelegatesToWebSocket() {
        // Arrange
        when(kiteWebSocket.isConnectToWebSocket()).thenReturn(true);
        List<Instrument> instruments = List.of();
        when(instrumentCache.getInstruments()).thenReturn(instruments);
        List<String> optionSymbols = List.of("NIFTY26FEB20000CE", "NIFTY26FEB20000PE");

        try (MockedStatic<OptionPriceUtils> mockedStatic = Mockito.mockStatic(OptionPriceUtils.class)) {
            mockedStatic.when(() -> OptionPriceUtils.getAllOptionSymbols(INDEX_SYMBOL, instruments))
                    .thenReturn(optionSymbols);

            // Act
            kiteService.appendAllOptionsForIndex(INDEX_SYMBOL);

            // Assert
            verify(kiteWebSocket).appendWebSocketSymbolsList(optionSymbols, false);
        }
    }

    @Test
    void testAppendAllOptionsForIndex_exceptionIsHandled() {
        // Arrange
        when(kiteWebSocket.isConnectToWebSocket()).thenReturn(true);
        when(instrumentCache.getInstruments()).thenThrow(new RuntimeException("Cache error"));

        // Act - should not throw
        kiteService.appendAllOptionsForIndex(INDEX_SYMBOL);

        // Assert - no subscription attempted
        verify(kiteWebSocket, never()).appendWebSocketSymbolsList(any(), anyBoolean());
    }
}
