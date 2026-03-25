package com.vish.fno.model.order.orderrequest;

import com.vish.fno.model.Task;
import com.vish.fno.model.Ticker;
import com.vish.fno.model.order.Target;
import com.vish.fno.model.util.ModelUtils;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.vish.fno.model.util.ModelUtils.getStringDateTime;
import static com.vish.fno.model.util.ModelUtils.roundTo5Paise;

/**
 * Tick-based order request for multi-target strategies. Mirrors {@link TickBasedOrderRequest}
 * but requires {@code target.size() >= 2} and {@code task.getLots() >= 2}.
 *
 * <p>{@link #verifyBuyThreshold(Ticker)} always returns {@code Optional.of(this)} because
 * tick-based strategies verify the threshold at tick level before creating the request.
 */
// CPD-OFF - Intentional structural similarity with TickBasedOrderRequest (multi-target variant)
@Slf4j
@Getter
@Builder
public final class MultiTargetTickOrderRequest implements OrderRequest {
    private static final int ESTIMATED_BUFFER_SIZE = 150;
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
    private int maxHoldDuration;
    private Map<String, String> extraData;

    @SuppressWarnings("PMD.ConfusingTernary")
    @Builder(builderMethodName = "builder")
    public MultiTargetTickOrderRequest(Task task, String tag, String index, String optionSymbol, Date date,
                                       int timestamp, int expirationTimestamp, double buyThreshold, Target target,
                                       double stopLoss, boolean callOrder, int maxHoldDuration,
                                       Map<String, String> extraData) {
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
        this.maxHoldDuration = maxHoldDuration;
        this.extraData = extraData != null ? extraData : Map.of();
    }

    public static MultiTargetTickOrderRequestBuilder builder(String tag, String index, Task task) {
        return new MultiTargetTickOrderRequestBuilder()
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
        final MultiTargetTickOrderRequest that = (MultiTargetTickOrderRequest) o;
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
        final StringBuilder sb = new StringBuilder(ESTIMATED_BUFFER_SIZE);
        sb.append(ModelUtils.INDENTED_TAB)
                .append("MultiTargetTickOrderRequest{")
                .append("date=").append(getStringDateTime(date))
                .append(", index=").append(index)
                .append(", tag=").append(tag)
                .append(", expiry=").append(expirationTimestamp)
                .append(", buyAt=").append(roundTo5Paise(buyThreshold))
                .append(", target=").append(target)
                .append(", stopLoss=").append(roundTo5Paise(stopLoss));

        if (isCallOrder()) {
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
