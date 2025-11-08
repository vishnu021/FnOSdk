package com.vish.fno.reader.model;

import com.zerodhatech.models.Order;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KiteOpenOrder {
    private Order order;
    private boolean isOrderPlaced;
    private Integer exceptionCode;
    private String exceptionMessage;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(75);
        sb.append("KiteOpenOrder{")
                .append("order=").append(order)
                .append(", isOrderPlaced=").append(isOrderPlaced);
        if (exceptionCode != null) {
            sb.append(", exceptionCode=").append(exceptionCode);
        }
        if (exceptionMessage != null) {
            sb.append(", exceptionMessage='").append(exceptionMessage).append('\'');
        }
        sb.append('}');
        return sb.toString();
    }
}
