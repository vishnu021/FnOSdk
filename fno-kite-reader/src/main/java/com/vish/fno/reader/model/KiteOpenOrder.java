package com.vish.fno.reader.model;

/**
 * Result of a Kite order placement attempt.
 *
 * @param orderId          Kite order ID (null if order was not placed or failed)
 * @param isOrderPlaced    true if the order was successfully placed with the broker
 * @param exceptionCode    Kite error code (null on success)
 * @param exceptionMessage Kite error message (null on success)
 */
public record KiteOpenOrder(
    String orderId,
    boolean isOrderPlaced,
    Integer exceptionCode,
    String exceptionMessage
) {
}
