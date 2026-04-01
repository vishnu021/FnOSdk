package com.vish.fno.reader.util;

import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.OrderParams;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
class OrderUtilsTest {

    @Test
    void testCreateBuyOrder() {
        // Given
        String symbol = "NIFTY24AUG24500CE";
        int orderSize = 50;
        String transactionType = Constants.TRANSACTION_TYPE_BUY;
        String tag = "testBuyTag";
        String exchange = Constants.EXCHANGE_NFO;

        // When
        OrderParams orderParams = OrderUtils.createMarketOrderWithParameters(symbol, orderSize, transactionType, tag, exchange);

        // Then
        assertEquals(50, orderParams.quantity);
        assertEquals(Constants.ORDER_TYPE_MARKET, orderParams.orderType);
        assertEquals("NIFTY24AUG24500CE", orderParams.tradingsymbol);
        assertEquals(Constants.PRODUCT_MIS, orderParams.product);
        assertEquals(Constants.EXCHANGE_NFO, orderParams.exchange);
        assertEquals(Constants.VALIDITY_DAY, orderParams.validity);
        assertEquals(Constants.TRANSACTION_TYPE_BUY, orderParams.transactionType);
        assertEquals(0.0, orderParams.triggerPrice);
        assertEquals(-1, orderParams.marketProtection);
        assertEquals("testBuyTag", orderParams.tag);
    }

    @Test
    void testCreateSellOrder() {
        // Given
        String symbol = "NIFTY24AUG24500PE";
        int orderSize = 75;
        String transactionType = Constants.TRANSACTION_TYPE_SELL;
        String tag = "testSellTag";
        String exchange = Constants.EXCHANGE_NFO;

        // When
        OrderParams orderParams = OrderUtils.createMarketOrderWithParameters(symbol, orderSize, transactionType, tag, exchange);

        // Then
        assertEquals(Constants.TRANSACTION_TYPE_SELL, orderParams.transactionType);
        assertEquals(75, orderParams.quantity);
    }

    @Test
    void testTagTruncatedWhenOver20Chars() {
        // Given
        String longTag = "thisTagIsWayTooLongForKite";

        // When
        OrderParams orderParams = OrderUtils.createMarketOrderWithParameters(
                "NIFTY24AUG24500CE", 50, Constants.TRANSACTION_TYPE_BUY, longTag, Constants.EXCHANGE_NFO);

        // Then
        assertEquals(20, orderParams.tag.length());
        assertEquals("thisTagIsWayTooLongF", orderParams.tag);
    }

    @Test
    void testTagPreservedWhenExactly20Chars() {
        // Given
        String exactTag = "12345678901234567890";

        // When
        OrderParams orderParams = OrderUtils.createMarketOrderWithParameters(
                "NIFTY24AUG24500CE", 50, Constants.TRANSACTION_TYPE_BUY, exactTag, Constants.EXCHANGE_NFO);

        // Then
        assertEquals(20, orderParams.tag.length());
        assertEquals("12345678901234567890", orderParams.tag);
    }

    @Test
    void testTagPreservedWhenUnder20Chars() {
        // Given
        String shortTag = "shortTag";

        // When
        OrderParams orderParams = OrderUtils.createMarketOrderWithParameters(
                "NIFTY24AUG24500CE", 50, Constants.TRANSACTION_TYPE_BUY, shortTag, Constants.EXCHANGE_NFO);

        // Then
        assertEquals("shortTag", orderParams.tag);
    }

    @Test
    void testEmptyTag() {
        // Given
        String emptyTag = "";

        // When
        OrderParams orderParams = OrderUtils.createMarketOrderWithParameters(
                "NIFTY24AUG24500CE", 50, Constants.TRANSACTION_TYPE_BUY, emptyTag, Constants.EXCHANGE_NFO);

        // Then
        assertEquals("", orderParams.tag);
    }

    @Test
    void testDifferentExchanges() {
        // Given / When
        OrderParams nfoOrder = OrderUtils.createMarketOrderWithParameters(
                "NIFTY24AUG24500CE", 50, Constants.TRANSACTION_TYPE_BUY, "nfoTag", Constants.EXCHANGE_NFO);
        OrderParams bfoOrder = OrderUtils.createMarketOrderWithParameters(
                "SENSEX24AUG24500CE", 50, Constants.TRANSACTION_TYPE_BUY, "bfoTag", Constants.EXCHANGE_BFO);

        // Then
        assertEquals(Constants.EXCHANGE_NFO, nfoOrder.exchange);
        assertEquals(Constants.EXCHANGE_BFO, bfoOrder.exchange);
    }
}
