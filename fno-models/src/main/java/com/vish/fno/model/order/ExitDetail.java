package com.vish.fno.model.order;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * ExitDetail - Immutable record for storing order exit details
 *
 * Records exit information when an order is sold (partially or fully).
 * Uses Jackson annotations to serialize only non-null fields.
 * All fields use wrapper types for consistency and flexibility.
 *
 * @param quantity Number of contracts/lots sold in this exit
 * @param sellPrice Index price at which the order was sold
 * @param sellOptionPrice Option price at which the order was sold (only for ActiveIndexOrder)
 * @param exitReason Reason for this exit (TARGET_HIT, STOP_LOSS_HIT, etc.)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExitDetail(
        Integer quantity,
        Double sellPrice,
        Double sellOptionPrice,  // Nullable for non-index orders
        OrderSellReason exitReason
) {
    /**
     * Factory method for creating ExitDetail for regular orders (non-index)
     */
    public static ExitDetail forRegularOrder(Integer quantity, Double sellPrice, OrderSellReason exitReason) {
        return new ExitDetail(quantity, sellPrice, null, exitReason);
    }

    /**
     * Factory method for creating ExitDetail for index-based orders
     */
    public static ExitDetail forIndexOrder(Integer quantity, Double sellPrice, Double sellOptionPrice,
                                           OrderSellReason exitReason) {
        return new ExitDetail(quantity, sellPrice, sellOptionPrice, exitReason);
    }
}
