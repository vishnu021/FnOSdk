package com.vish.fno.model.order.orderrequest;

import com.vish.fno.model.Task;
import com.vish.fno.model.Ticker;
import com.vish.fno.model.helper.EntryVerifier;
import com.vish.fno.model.helper.OptionEntryVerifier;
import com.vish.fno.model.util.ModelUtils;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.vish.fno.model.util.ModelUtils.getStringDateTime;
import static com.vish.fno.model.util.ModelUtils.roundTo5Paise;

@Slf4j
@Getter
@Builder
public class OptionBasedOrderRequest implements OrderRequest {
    private static final int estimated_buffer_size = 100;

    private final Task task;
    private final String tag;
    private final String index;
    private Date date;
    private int timestamp;
    private int expirationTimestamp;
    private double buyThreshold;
    private double target;
    private double stopLoss;
    private int quantity;
    private Map<String, String> extraData;
    private int lotSize;
    private EntryVerifier entryVerifier;

    @SuppressWarnings("PMD.ConfusingTernary")
    @Builder(builderMethodName = "builder")
    public OptionBasedOrderRequest(Task task, String tag, String index, Date date, int timestamp,
                                   int expirationTimestamp, double buyThreshold, double target, double stopLoss,
                                   int quantity, Map<String, String> extraData, int lotSize,
                                   EntryVerifier entryVerifier) {
        this.task = task;
        this.tag = tag == null ? "" : tag.replaceAll("[a-z]", "");
        this.index = index;
        this.date = date;
        this.timestamp = timestamp;
        this.expirationTimestamp = expirationTimestamp;
        this.buyThreshold = buyThreshold;
        this.target = target;
        this.stopLoss = stopLoss;
        this.quantity = quantity;
        this.extraData = extraData;
        this.lotSize = lotSize;
        this.entryVerifier = entryVerifier != null ? entryVerifier : new OptionEntryVerifier();
    }

    public static OptionBasedOrderRequestBuilder builder(String tag, String index, Task task) {
        return new OptionBasedOrderRequestBuilder()
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
        final OptionBasedOrderRequest that = (OptionBasedOrderRequest) o;
        return Objects.equals(tag, that.tag) && Objects.equals(index, that.index);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tag, index);
    }

    @Override
    public Optional<OrderRequest> verifyBuyThreshold(Ticker tick) {
        return entryVerifier.verifyBuyThreshold(tick, this);
    }

    @Override
    public boolean hasMoveAlreadyHappened(double ltp) {
        return entryVerifier.hasMoveAlreadyHappened(ltp, this);
    }

    @Override
    public boolean isPlaceOrder(double ltp, double availableCash, boolean isExpiryDayForOption) {
        return entryVerifier.isPlaceOrder(this, ltp, isExpiryDayForOption, availableCash);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(estimated_buffer_size);
        sb.append(ModelUtils.INDENTED_TAB)
                .append("OptionBasedOrderRequest{")
                .append("date=").append(getStringDateTime(date))
                .append(", index=").append(index)
                .append(", tag=").append(tag)
                .append(", expiry=").append(expirationTimestamp)
                .append(", buyAt=").append(roundTo5Paise(buyThreshold))
                .append(", target=").append(roundTo5Paise(target))
                .append(", stopLoss=").append(roundTo5Paise(stopLoss))
                .append(", risk=").append(roundTo5Paise(buyThreshold - stopLoss))
                .append(", reward=").append(roundTo5Paise(target - buyThreshold))
                .append('}');
        return sb.toString();
    }
}
