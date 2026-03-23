package com.vish.fno.model.order.orderrequest;

import com.vish.fno.model.Task;
import com.vish.fno.model.Ticker;
import com.vish.fno.model.order.OrderMetadata;
import com.vish.fno.model.order.Target;
import com.vish.fno.model.util.ModelUtils;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.Objects;
import java.util.Optional;

import static com.vish.fno.model.util.ModelUtils.getStringDateTime;
import static com.vish.fno.model.util.ModelUtils.roundTo5Paise;

// CPD-OFF
@Slf4j
@Getter
@Builder
public final class TickBasedOrderRequest implements OrderRequest {
    private static final int estimated_buffer_size = 100;
    private final Task task;
    private final String tag;
    private final String index;
    @Setter
    private String optionSymbol;
    private Date date;
    private int timestamp;
    private int expirationTimestamp;
    private double buyThreshold;
    private Target target;
    private double stopLoss;
    private boolean callOrder;
    private OrderMetadata orderMetadata;

    @SuppressWarnings("PMD.ConfusingTernary")
    @Builder(builderMethodName = "builder")
    public TickBasedOrderRequest(Task task, String tag, String index, String optionSymbol, Date date, int timestamp,
                                 int expirationTimestamp, double buyThreshold, Target target, double stopLoss,
                                 boolean callOrder, OrderMetadata orderMetadata) {
        this.task = task;
        this.tag = tag == null ? "" : tag;
        this.index = index;
        this.optionSymbol = optionSymbol;
        this.date = date;
        this.timestamp = timestamp;
        this.expirationTimestamp = expirationTimestamp;
        this.buyThreshold = buyThreshold;
        this.target = target;
        this.stopLoss = stopLoss;
        this.callOrder = callOrder;
        this.orderMetadata = orderMetadata;
    }

    public static class TickBasedOrderRequestBuilder {
        public TickBasedOrderRequestBuilder target(double target) {
            this.target = Target.of(target);
            return this;
        }
    }

    public static TickBasedOrderRequestBuilder builder(String tag, String index, Task task) {
        return new TickBasedOrderRequestBuilder()
                .tag(tag)
                .index(index)
                .task(task);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TickBasedOrderRequest that = (TickBasedOrderRequest) o;
        return Objects.equals(tag, that.tag)
                && Objects.equals(index, that.index)
                && Objects.equals(callOrder, that.callOrder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tag, index, callOrder);
    }

    @Override
    public Optional<OrderRequest> verifyBuyThreshold(Ticker tick) {
        return Optional.of(this);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(estimated_buffer_size);
        sb.append(ModelUtils.INDENTED_TAB)
                .append("IndexOrderRequest{")
                .append("date=").append(getStringDateTime(date))
                .append(", index=").append(index)
                .append(", tag=").append(tag)
                .append(", expiry=").append(expirationTimestamp)
                .append(", buyAt=").append(roundTo5Paise(buyThreshold))
                .append(", target=").append(target)
                .append(", stopLoss=").append(roundTo5Paise(stopLoss));

        if(isCallOrder()) {
            sb.append(", risk=").append(roundTo5Paise(buyThreshold - stopLoss))
                    .append(", reward=").append(roundTo5Paise(target.first() - buyThreshold));
        } else {
            sb.append(", risk=").append(roundTo5Paise(stopLoss - buyThreshold))
                    .append(", reward=").append(roundTo5Paise(buyThreshold - target.first()));
        }

        sb.append(", CE=").append(isCallOrder())
                .append('}');
        return sb.toString();
    }
}
// CPD-ON