package com.vish.fno.reader.shoonya.model;

/**
 * Result of a Shoonya order placement attempt.
 *
 * @param orderId        Shoonya order number (norenordno), null if failed
 * @param isOrderPlaced  true if order was placed (or test mode), false on error
 * @param errorMessage   error message from API (emsg field), null on success
 */
public record ShoonyaOpenOrder(
    String orderId,
    boolean isOrderPlaced,
    String errorMessage
) {
}
