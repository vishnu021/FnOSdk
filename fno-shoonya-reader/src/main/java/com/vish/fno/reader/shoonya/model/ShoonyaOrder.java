package com.vish.fno.reader.shoonya.model;

/**
 * Order book entry from Shoonya's OrderBook API response.
 *
 * @param orderId          Shoonya order number (norenordno)
 * @param exchange         exchange code (NSE, NFO, BSE, BFO)
 * @param tradingSymbol    trading symbol
 * @param status           order status (OPEN, COMPLETE, CANCELED, REJECTED)
 * @param transactionType  B (buy) or S (sell)
 * @param quantity         order quantity
 * @param filledQuantity   filled quantity (fillshares)
 * @param averagePrice     average fill price
 * @param remarks          order tag/remarks
 */
public record ShoonyaOrder(
    String orderId,
    String exchange,
    String tradingSymbol,
    String status,
    String transactionType,
    int quantity,
    int filledQuantity,
    double averagePrice,
    String remarks
) {
}
