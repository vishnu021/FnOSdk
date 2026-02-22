package com.vish.fno.reader.core;

import com.vish.fno.reader.model.KiteOpenOrder;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KiteOrderExecutorTest {

    private static final String SYMBOL = "NIFTY2610626000CE";
    private static final String TAG = "testTag";
    private static final String EXCHANGE = "NFO";
    private static final int ORDER_SIZE = 50;

    @Mock
    private KiteSession session;
    @Mock
    private InstrumentCache instrumentCache;
    @Mock
    private KiteConnect mockKiteSdk;

    private KiteOrderExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new KiteOrderExecutor(session, instrumentCache);
    }

    // -----------------------------------------------------------------------
    // Helper: make session.executeWithLock actually invoke the Supplier
    // -----------------------------------------------------------------------
    @SuppressWarnings("unchecked")
    private void mockExecuteWithLock() {
        when(session.executeWithLock(any(Supplier.class), anyString()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(0);
                    return supplier.get();
                });
    }

    // =======================================================================
    // buyOrder tests
    // =======================================================================

    @Test
    void testBuyOrder_notInitialised() {
        // Arrange
        when(session.isInitialised()).thenReturn(false);

        // Act
        Optional<KiteOpenOrder> result = executor.buyOrder(SYMBOL, ORDER_SIZE, TAG, true);

        // Assert
        assertTrue(result.isPresent());
        KiteOpenOrder openOrder = result.get();
        assertNull(openOrder.order());
        assertFalse(openOrder.isOrderPlaced());
        assertNull(openOrder.exceptionCode());
        assertNull(openOrder.exceptionMessage());
    }

    @Test
    void testBuyOrder_isPlaceOrderFalse() {
        // Arrange
        when(session.isInitialised()).thenReturn(true);

        // Act
        Optional<KiteOpenOrder> result = executor.buyOrder(SYMBOL, ORDER_SIZE, TAG, false);

        // Assert
        assertTrue(result.isPresent());
        KiteOpenOrder openOrder = result.get();
        assertNull(openOrder.order());
        assertTrue(openOrder.isOrderPlaced());
        assertNull(openOrder.exceptionCode());
        assertNull(openOrder.exceptionMessage());
    }

    @Test
    void testBuyOrder_placeOrdersDisabledByConfig() {
        // Arrange
        when(session.isInitialised()).thenReturn(true);
        when(session.isPlaceOrders()).thenReturn(false);

        // Act
        Optional<KiteOpenOrder> result = executor.buyOrder(SYMBOL, ORDER_SIZE, TAG, true);

        // Assert
        assertTrue(result.isPresent());
        KiteOpenOrder openOrder = result.get();
        assertNull(openOrder.order());
        assertTrue(openOrder.isOrderPlaced());
        assertNull(openOrder.exceptionCode());
        assertNull(openOrder.exceptionMessage());
    }

    @Test
    void testBuyOrder_success() throws Throwable {
        // Arrange
        when(session.isInitialised()).thenReturn(true);
        when(session.isPlaceOrders()).thenReturn(true);
        mockExecuteWithLock();
        when(session.getKiteSdk()).thenReturn(mockKiteSdk);
        when(instrumentCache.getExchangeForSymbol(SYMBOL)).thenReturn(EXCHANGE);

        Order expectedOrder = new Order();
        expectedOrder.orderId = "ORDER123";
        when(mockKiteSdk.placeOrder(any(OrderParams.class), eq(Constants.VARIETY_REGULAR)))
                .thenReturn(expectedOrder);

        // Act
        Optional<KiteOpenOrder> result = executor.buyOrder(SYMBOL, ORDER_SIZE, TAG, true);

        // Assert
        assertTrue(result.isPresent());
        KiteOpenOrder openOrder = result.get();
        assertNotNull(openOrder.order());
        assertEquals("ORDER123", openOrder.order().orderId);
        assertTrue(openOrder.isOrderPlaced());
        assertNull(openOrder.exceptionCode());
        assertNull(openOrder.exceptionMessage());

        verify(mockKiteSdk).placeOrder(any(OrderParams.class), eq(Constants.VARIETY_REGULAR));
    }

    @Test
    void testBuyOrder_kiteException() throws Throwable {
        // Arrange
        when(session.isInitialised()).thenReturn(true);
        when(session.isPlaceOrders()).thenReturn(true);
        mockExecuteWithLock();
        when(session.getKiteSdk()).thenReturn(mockKiteSdk);
        when(instrumentCache.getExchangeForSymbol(SYMBOL)).thenReturn(EXCHANGE);

        KiteException kiteException = new KiteException("Insufficient funds", 403);
        when(mockKiteSdk.placeOrder(any(OrderParams.class), eq(Constants.VARIETY_REGULAR)))
                .thenThrow(kiteException);

        // Act
        Optional<KiteOpenOrder> result = executor.buyOrder(SYMBOL, ORDER_SIZE, TAG, true);

        // Assert
        assertTrue(result.isPresent());
        KiteOpenOrder openOrder = result.get();
        assertNull(openOrder.order());
        assertFalse(openOrder.isOrderPlaced());
        assertEquals(403, openOrder.exceptionCode());
        assertEquals("Insufficient funds", openOrder.exceptionMessage());
    }

    @Test
    void testBuyOrder_ioException() throws Throwable {
        // Arrange
        when(session.isInitialised()).thenReturn(true);
        when(session.isPlaceOrders()).thenReturn(true);
        mockExecuteWithLock();
        when(session.getKiteSdk()).thenReturn(mockKiteSdk);
        when(instrumentCache.getExchangeForSymbol(SYMBOL)).thenReturn(EXCHANGE);

        IOException ioException = new IOException("Connection timeout");
        when(mockKiteSdk.placeOrder(any(OrderParams.class), eq(Constants.VARIETY_REGULAR)))
                .thenThrow(ioException);

        // Act
        Optional<KiteOpenOrder> result = executor.buyOrder(SYMBOL, ORDER_SIZE, TAG, true);

        // Assert
        assertTrue(result.isPresent());
        KiteOpenOrder openOrder = result.get();
        assertNull(openOrder.order());
        assertFalse(openOrder.isOrderPlaced());
        assertNull(openOrder.exceptionCode());
        assertEquals("Connection timeout", openOrder.exceptionMessage());
    }

    // =======================================================================
    // sellOrder tests
    // =======================================================================

    @Test
    void testSellOrder_success() throws Throwable {
        // Arrange
        when(session.isInitialised()).thenReturn(true);
        when(session.isPlaceOrders()).thenReturn(true);
        mockExecuteWithLock();
        when(session.getKiteSdk()).thenReturn(mockKiteSdk);
        when(instrumentCache.getExchangeForSymbol(SYMBOL)).thenReturn(EXCHANGE);

        Order expectedOrder = new Order();
        expectedOrder.orderId = "SELL456";
        when(mockKiteSdk.placeOrder(any(OrderParams.class), eq(Constants.VARIETY_REGULAR)))
                .thenReturn(expectedOrder);

        // Act
        Optional<KiteOpenOrder> result = executor.sellOrder(SYMBOL, ORDER_SIZE, TAG, true);

        // Assert
        assertTrue(result.isPresent());
        KiteOpenOrder openOrder = result.get();
        assertNotNull(openOrder.order());
        assertEquals("SELL456", openOrder.order().orderId);
        assertTrue(openOrder.isOrderPlaced());
        assertNull(openOrder.exceptionCode());
        assertNull(openOrder.exceptionMessage());

        verify(mockKiteSdk).placeOrder(any(OrderParams.class), eq(Constants.VARIETY_REGULAR));
    }

    @Test
    void testSellOrder_notInitialised() {
        // Arrange
        when(session.isInitialised()).thenReturn(false);

        // Act
        Optional<KiteOpenOrder> result = executor.sellOrder(SYMBOL, ORDER_SIZE, TAG, true);

        // Assert
        assertTrue(result.isPresent());
        KiteOpenOrder openOrder = result.get();
        assertNull(openOrder.order());
        assertFalse(openOrder.isOrderPlaced());
        assertNull(openOrder.exceptionCode());
        assertNull(openOrder.exceptionMessage());
    }

    // =======================================================================
    // placeOptionOrder tests
    // =======================================================================

    @Test
    void testPlaceOptionOrder_success() throws Throwable {
        // Arrange
        mockExecuteWithLock();
        when(session.getKiteSdk()).thenReturn(mockKiteSdk);

        OrderParams orderParams = new OrderParams();
        orderParams.tradingsymbol = SYMBOL;
        orderParams.quantity = ORDER_SIZE;

        Order expectedOrder = new Order();
        expectedOrder.orderId = "OPT789";
        when(mockKiteSdk.placeOrder(eq(orderParams), eq(Constants.VARIETY_REGULAR)))
                .thenReturn(expectedOrder);

        // Act
        Order result = executor.placeOptionOrder(orderParams);

        // Assert
        assertNotNull(result);
        assertEquals("OPT789", result.orderId);
        verify(mockKiteSdk).placeOrder(eq(orderParams), eq(Constants.VARIETY_REGULAR));
    }

    @Test
    void testPlaceOptionOrder_kiteException() throws Throwable {
        // Arrange
        mockExecuteWithLock();
        when(session.getKiteSdk()).thenReturn(mockKiteSdk);

        OrderParams orderParams = new OrderParams();
        orderParams.tradingsymbol = SYMBOL;

        KiteException kiteException = new KiteException("Order rejected", 400);
        when(mockKiteSdk.placeOrder(eq(orderParams), eq(Constants.VARIETY_REGULAR)))
                .thenThrow(kiteException);

        // Act
        Order result = executor.placeOptionOrder(orderParams);

        // Assert
        assertNull(result);
    }

    @Test
    void testPlaceOptionOrder_ioException() throws Throwable {
        // Arrange
        mockExecuteWithLock();
        when(session.getKiteSdk()).thenReturn(mockKiteSdk);

        OrderParams orderParams = new OrderParams();
        orderParams.tradingsymbol = SYMBOL;

        when(mockKiteSdk.placeOrder(eq(orderParams), eq(Constants.VARIETY_REGULAR)))
                .thenThrow(new IOException("Network error"));

        // Act
        Order result = executor.placeOptionOrder(orderParams);

        // Assert
        assertNull(result);
    }

    // =======================================================================
    // getOrders tests
    // =======================================================================

    @Test
    void testGetOrders_success() throws Throwable {
        // Arrange
        mockExecuteWithLock();
        when(session.getKiteSdk()).thenReturn(mockKiteSdk);

        Order order1 = new Order();
        order1.orderId = "ORD001";
        Order order2 = new Order();
        order2.orderId = "ORD002";
        List<Order> expectedOrders = List.of(order1, order2);
        when(mockKiteSdk.getOrders()).thenReturn(expectedOrders);

        // Act
        List<Order> result = executor.getOrders();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("ORD001", result.get(0).orderId);
        assertEquals("ORD002", result.get(1).orderId);
        verify(mockKiteSdk).getOrders();
    }

    @Test
    void testGetOrders_kiteException() throws Throwable {
        // Arrange
        mockExecuteWithLock();
        when(session.getKiteSdk()).thenReturn(mockKiteSdk);

        KiteException kiteException = new KiteException("Unauthorized", 401);
        when(mockKiteSdk.getOrders()).thenThrow(kiteException);

        // Act
        List<Order> result = executor.getOrders();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetOrders_ioException() throws Throwable {
        // Arrange
        mockExecuteWithLock();
        when(session.getKiteSdk()).thenReturn(mockKiteSdk);

        when(mockKiteSdk.getOrders()).thenThrow(new IOException("Read timed out"));

        // Act
        List<Order> result = executor.getOrders();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // =======================================================================
    // getPositions tests
    // =======================================================================

    @Test
    void testGetPositions_success() throws Throwable {
        // Arrange
        mockExecuteWithLock();
        when(session.getKiteSdk()).thenReturn(mockKiteSdk);

        Position position = new Position();
        position.tradingSymbol = SYMBOL;
        Map<String, List<Position>> expectedPositions = Map.of(
                "net", List.of(position),
                "day", List.of()
        );
        when(mockKiteSdk.getPositions()).thenReturn(expectedPositions);

        // Act
        Map<String, List<Position>> result = executor.getPositions();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.containsKey("net"));
        assertTrue(result.containsKey("day"));
        assertEquals(1, result.get("net").size());
        assertEquals(SYMBOL, result.get("net").get(0).tradingSymbol);
        verify(mockKiteSdk).getPositions();
    }

    @Test
    void testGetPositions_kiteException() throws Throwable {
        // Arrange
        mockExecuteWithLock();
        when(session.getKiteSdk()).thenReturn(mockKiteSdk);

        KiteException kiteException = new KiteException("Session expired", 403);
        when(mockKiteSdk.getPositions()).thenThrow(kiteException);

        // Act
        Map<String, List<Position>> result = executor.getPositions();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
