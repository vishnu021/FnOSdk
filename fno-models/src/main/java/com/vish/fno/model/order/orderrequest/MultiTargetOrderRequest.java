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

/**
 * Order request for multi-target strategies. Mirrors {@link IndexOrderRequest} but requires
 * {@code target.size() >= 2} and {@code task.getLots() >= 2}.
 *
 * <p>Strategies create this when they want partial exits at multiple target levels.
 * The {@link com.vish.fno.model.order.ActiveOrderFactory} creates a
 * {@link com.vish.fno.model.order.activeorder.MultiTargetActiveIndexOrder} from this request.
 */
// CPD-OFF - Intentional structural similarity with IndexOrderRequest (multi-target variant)
@Slf4j
@Getter
@Builder
public final class MultiTargetOrderRequest implements OrderRequest {
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
    private OrderMetadata orderMetadata;

    @SuppressWarnings("PMD.ConfusingTernary")
    @Builder(builderMethodName = "builder")
    public MultiTargetOrderRequest(Task task, String tag, String index, String optionSymbol, Date date,
                                   int timestamp, int expirationTimestamp, double buyThreshold, Target target,
                                   double stopLoss, boolean callOrder, OrderMetadata orderMetadata) {
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

    public static MultiTargetOrderRequestBuilder builder(String tag, String index, Task task) {
        return new MultiTargetOrderRequestBuilder()
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
        final MultiTargetOrderRequest that = (MultiTargetOrderRequest) o;
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
        if (callOrder) {
            if (tick.lastTradedPrice() > buyThreshold) {
                log.info("tick ltp: {} is greater than buy threshold {}, placing multi-target order request({}) : {}",
                        tick.lastTradedPrice(), buyThreshold, optionSymbol, this);
                return Optional.of(this);
            }
        } else {
            if (tick.lastTradedPrice() < buyThreshold) {
                log.info("tick ltp: {} is lesser than buy threshold {}, placing multi-target order request({}) : {}",
                        tick.lastTradedPrice(), buyThreshold, optionSymbol, this);
                return Optional.of(this);
            }
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(ESTIMATED_BUFFER_SIZE);
        sb.append(ModelUtils.INDENTED_TAB)
                .append("MultiTargetOrderRequest{")
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
