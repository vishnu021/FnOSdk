package com.vish.fno.reader.model;

import com.zerodhatech.models.Order;

public record KiteOpenOrder(
    Order order,
    boolean isOrderPlaced,
    Integer exceptionCode,
    String exceptionMessage
) {
}
