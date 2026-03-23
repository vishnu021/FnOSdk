package com.vish.fno.model.order.activeorder;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vish.fno.model.order.OrderMetadata;
import com.vish.fno.model.order.orderrequest.MultiTargetTickOrderRequest;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Active order for multi-target tick-based strategies. Mirrors {@link TickBasedActiveOrder}
 * but tracks multi-target state via the {@link MultiTargetOrder} interface.
 */
// CPD-OFF - Intentional structural similarity with TickBasedActiveOrder (multi-target variant)
@Getter
public final class MultiTargetTickActiveOrder extends AbstractActiveOrder implements MultiTargetOrder {
    private final String optionSymbol;
    private final int lotSize;
    private double optionBuyPrice;
    @Setter
    private double optionSellPrice;

    private int currentTargetIndex;
    private final List<Integer> targetQuantities;

    public MultiTargetTickActiveOrder(MultiTargetTickOrderRequest openOrder, double buyPrice, int timestampIndex,
                                      String timestamp, int quantity, int lotSize) {
        super(openOrder, buyPrice, timestampIndex, quantity, timestamp);
        this.optionSymbol = openOrder.getOptionSymbol();
        this.lotSize = lotSize;
        this.currentTargetIndex = 0;
        int totalLots = lotSize > 0 ? quantity / lotSize : 0;
        this.targetQuantities = computeQuantities(openOrder.getTarget().size(), totalLots, lotSize);
        copyExtras(openOrder.getOrderMetadata());
    }

    private void copyExtras(OrderMetadata orderMetadata) {
        if (orderMetadata != null && orderMetadata.getSubSignal() != null) {
            this.extraData.put("subSignal", orderMetadata.getSubSignal());
        }
    }

    // --- MultiTargetOrder API ---

    @Override
    public void advanceTarget() {
        if (currentTargetIndex < orderRequest.getTarget().size()) {
            updateStopLoss(orderRequest.getTarget().get(currentTargetIndex));
            currentTargetIndex++;
        }
    }

    @Override
    public int getCurrentTargetQuantity() {
        if (currentTargetIndex < targetQuantities.size()) {
            return targetQuantities.get(currentTargetIndex);
        }
        return getBuyQuantity() - getSoldQuantity();
    }

    @Override
    public boolean hasMoreTargets() {
        return currentTargetIndex < orderRequest.getTarget().size();
    }

    @Override
    public double getCurrentTarget() {
        if (currentTargetIndex < orderRequest.getTarget().size()) {
            return orderRequest.getTarget().get(currentTargetIndex);
        }
        return orderRequest.getTarget().get(orderRequest.getTarget().size() - 1);
    }

    // --- Standard ActiveOrder methods (same as TickBasedActiveOrder) ---

    @JsonIgnore
    @Override
    public boolean isCallOrder() {
        return orderRequest.isCallOrder();
    }

    @Override
    public void setStopLoss(double stopLoss) {
        if (this.isCallOrder()) {
            if (this.stopLoss < stopLoss) {
                updateStopLoss(stopLoss);
            }
        } else {
            if (this.stopLoss > stopLoss) {
                updateStopLoss(stopLoss);
            }
        }
    }

    @Override
    public String getTradingSymbol() {
        return this.getOptionSymbol();
    }

    @JsonIgnore
    @Override
    public double getProfit() {
        if (isCallOrder()) {
            return (getSellPrice() - getBuyPrice()) * this.getBuyQuantity();
        } else {
            return (getBuyPrice() - getSellPrice()) * this.getBuyQuantity();
        }
    }

    @Override
    public void setOptionBuyPrice(double price) {
        this.optionBuyPrice = price;
        initializeRealisedProfit(price);
    }

    @Override
    public void incrementSoldQuantity(int soldQuantity, double sellOptionPrice) {
        super.incrementSoldQuantity(soldQuantity, sellOptionPrice);
        this.optionSellPrice = sellOptionPrice;
    }

    @Override
    protected void appendToStringFields(StringBuilder sb) {
        sb.append(", optionSymbol=").append(optionSymbol)
          .append(", targetIndex=").append(currentTargetIndex)
          .append("/").append(orderRequest.getTarget().size());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MultiTargetTickActiveOrder that = (MultiTargetTickActiveOrder) o;
        return Objects.equals(orderRequest.getTag(), that.orderRequest.getTag())
                && Objects.equals(orderRequest.getIndex(), that.orderRequest.getIndex())
                && Objects.equals(isCallOrder(), that.isCallOrder());
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderRequest.getTag(), orderRequest.getIndex(), isCallOrder());
    }

    @SuppressWarnings("PMD.AvoidArrayLoops")
    static List<Integer> computeQuantities(int numTargets, int totalLots, int lotSize) {
        int lotsPerTarget = totalLots / numTargets;
        int remainderLots = totalLots % numTargets;
        List<Integer> qtys = new ArrayList<>();
        for (int i = 0; i < numTargets - 1; i++) {
            qtys.add(lotsPerTarget * lotSize);
        }
        qtys.add((lotsPerTarget + remainderLots) * lotSize);
        return List.copyOf(qtys);
    }
}
// CPD-ON
