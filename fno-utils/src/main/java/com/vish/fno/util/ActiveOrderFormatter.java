package com.vish.fno.util;

import com.vish.fno.model.order.activeorder.ActiveIndexOrder;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import com.vish.fno.model.order.activeorder.OptionBasedActiveOrder;
import com.vish.fno.model.order.activeorder.TickBasedActiveOrder;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static com.vish.fno.model.util.ModelUtils.getStringDate;
import static com.vish.fno.model.util.ModelUtils.getStringDateTime;
import static com.vish.fno.model.util.ModelUtils.roundTo5Paise;

/**
 * Utility class for formatting ActiveOrder instances to CSV and log formats.
 * Provides static methods for generating CSV headers, CSV rows, and order logs.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ActiveOrderFormatter {
    private static final int ESTIMATED_BUFFER_SIZE = 125;
    private static final int CSV_HEADER_BUFFER_SIZE = 250;

    /**
     * Generates CSV header for ActiveOrder data.
     *
     * @param order ActiveOrder instance to extract extra data keys from
     * @return CSV header string
     */
    public static String csvHeader(ActiveOrder order) {
        final StringBuilder sb = new StringBuilder(CSV_HEADER_BUFFER_SIZE);

        sb.append("symbol").append(",")
                .append("entryTimeStamp").append(",")
                .append("exitTimeStamp").append(",")
                .append("buyThreshold").append(",")
                .append("buyPrice").append(",")
                .append("target").append(",")
                .append("sellPrice").append(",")
                .append("stopLoss").append(",")
                .append("profit").append(",")
                .append("quantity").append(",")
                .append("reward").append(",")
                .append("call");

        if (order.getExtraData() != null) {
            for (String key : order.getExtraData().keySet()) {
                sb.append(",").append(key);
            }
        }
        return sb.toString();
    }

    /**
     * Converts ActiveOrder to CSV format.
     * Handles type-specific formatting for ActiveIndexOrder, TickBasedActiveOrder, and OptionBasedActiveOrder.
     *
     * @param order ActiveOrder instance to convert
     * @return CSV formatted string
     */
    public static String toCSV(ActiveOrder order) {
        if (order instanceof ActiveIndexOrder) {
            return formatActiveIndexOrderToCSV((ActiveIndexOrder) order);
        } else if (order instanceof TickBasedActiveOrder) {
            return formatTickBasedActiveOrderToCSV((TickBasedActiveOrder) order);
        } else if (order instanceof OptionBasedActiveOrder) {
            return formatOptionBasedActiveOrderToCSV((OptionBasedActiveOrder) order);
        } else {
            throw new IllegalArgumentException("Unsupported ActiveOrder type: " + order.getClass().getName());
        }
    }

    /**
     * Generates order log string for ActiveOrder.
     * Handles type-specific formatting for ActiveIndexOrder, TickBasedActiveOrder, and OptionBasedActiveOrder.
     *
     * @param order ActiveOrder instance to log
     * @return Formatted order log string
     */
    public static String orderLog(ActiveOrder order) {
        if (order instanceof ActiveIndexOrder) {
            return formatActiveIndexOrderLog((ActiveIndexOrder) order);
        } else if (order instanceof TickBasedActiveOrder) {
            return formatTickBasedActiveOrderLog((TickBasedActiveOrder) order);
        } else if (order instanceof OptionBasedActiveOrder) {
            return formatOptionBasedActiveOrderLog((OptionBasedActiveOrder) order);
        } else {
            throw new IllegalArgumentException("Unsupported ActiveOrder type: " + order.getClass().getName());
        }
    }

    // CPD-OFF
    private static String formatActiveIndexOrderToCSV(ActiveIndexOrder order) {
        final StringBuilder sb = new StringBuilder(ESTIMATED_BUFFER_SIZE);
        sb.append(order.getIndex())
                .append(',').append(' ').append(getStringDate(order.getDate()))
                .append(' ').append(order.getEntryTimeStamp())
                .append(',').append(' ').append(getStringDate(order.getDate()))
                .append(' ').append(order.getExitTimeStamp())
                .append(',').append(' ').append(roundTo5Paise(order.getBuyThreshold()))
                .append(',').append(' ').append(roundTo5Paise(order.getBuyPrice()))
                .append(',').append(' ').append(roundTo5Paise(order.getTarget()))
                .append(',').append(' ').append(roundTo5Paise(order.getSellPrice()))
                .append(',').append(' ').append(roundTo5Paise(order.getStopLoss()))
                .append(',').append(' ').append(roundTo5Paise(order.getProfit()))
                .append(',').append(' ').append(roundTo5Paise(order.getBuyQuantity()));

        if (order.isCallOrder()) {
            sb.append(',').append(' ').append(roundTo5Paise(order.getTarget() - order.getBuyThreshold()));
        } else {
            sb.append(',').append(' ').append(roundTo5Paise(order.getBuyThreshold() - order.getTarget()));
        }
        sb.append(',').append(' ').append(order.isCallOrder());
        if (order.getExtraData() != null) {
            for (String key : order.getExtraData().keySet()) {
                sb.append(',').append(' ').append(order.getExtraData().get(key));
            }
        }
        return sb.toString();
    }

    private static String formatTickBasedActiveOrderToCSV(TickBasedActiveOrder order) {
        final StringBuilder sb = new StringBuilder(ESTIMATED_BUFFER_SIZE);
        sb.append(order.getIndex())
                .append(',').append(' ').append(getStringDate(order.getDate()))
                .append(' ').append(order.getEntryTimeStamp())
                .append(',').append(' ').append(getStringDate(order.getDate()))
                .append(' ').append(order.getExitTimeStamp())
                .append(',').append(' ').append(roundTo5Paise(order.getBuyThreshold()))
                .append(',').append(' ').append(roundTo5Paise(order.getBuyPrice()))
                .append(',').append(' ').append(roundTo5Paise(order.getTarget()))
                .append(',').append(' ').append(roundTo5Paise(order.getSellPrice()))
                .append(',').append(' ').append(roundTo5Paise(order.getStopLoss()))
                .append(',').append(' ').append(roundTo5Paise(order.getProfit()))
                .append(',').append(' ').append(roundTo5Paise(order.getBuyQuantity()));

        if (order.isCallOrder()) {
            sb.append(',').append(' ').append(roundTo5Paise(order.getTarget() - order.getBuyThreshold()));
        } else {
            sb.append(',').append(' ').append(roundTo5Paise(order.getBuyThreshold() - order.getTarget()));
        }
        sb.append(',').append(' ').append(order.isCallOrder());
        if (order.getExtraData() != null) {
            for (String key : order.getExtraData().keySet()) {
                sb.append(',').append(' ').append(order.getExtraData().get(key));
            }
        }
        return sb.toString();
    }

    private static String formatOptionBasedActiveOrderToCSV(OptionBasedActiveOrder order) {
        final StringBuilder sb = new StringBuilder(ESTIMATED_BUFFER_SIZE);
        sb.append(order.getIndex())
                .append(',').append(' ').append(getStringDate(order.getDate()))
                .append(' ').append(order.getEntryTimeStamp())
                .append(',').append(' ').append(getStringDate(order.getDate()))
                .append(' ').append(order.getExitTimeStamp())
                .append(',').append(' ').append(roundTo5Paise(order.getBuyThreshold()))
                .append(',').append(' ').append(roundTo5Paise(order.getBuyPrice()))
                .append(',').append(' ').append(roundTo5Paise(order.getTarget()))
                .append(',').append(' ').append(roundTo5Paise(order.getSellPrice()))
                .append(',').append(' ').append(roundTo5Paise(order.getStopLoss()))
                .append(',').append(' ').append(roundTo5Paise(order.getProfit()))
                .append(',').append(' ').append(roundTo5Paise(order.getBuyQuantity()))
                .append(',').append(' ').append(roundTo5Paise(order.getTarget() - order.getBuyThreshold()));

        if (order.getExtraData() != null) {
            for (String key : order.getExtraData().keySet()) {
                sb.append(',').append(' ').append(order.getExtraData().get(key));
            }
        }
        return sb.toString();
    }

    private static String formatActiveIndexOrderLog(ActiveIndexOrder order) {
        final StringBuilder sb = new StringBuilder(ESTIMATED_BUFFER_SIZE);
        sb.append("OrderLog{")
                .append("index='").append(order.getIndex()).append("'")
                .append(",\ttag=").append(order.getTag())
                .append(",\tentry=").append(getStringDateTime(order.getDate()))
                .append(",\texit=").append(order.getExitTimeStamp())
                .append(",\tbuy=").append(roundTo5Paise(order.getBuyPrice()))
                .append(",\ttarget=").append(roundTo5Paise(order.getTarget()))
                .append(",\tsell=").append(roundTo5Paise(order.getSellPrice()))
                .append(",\tcall=").append(order.isCallOrder());

        if (order.getProfit() > 0) {
            sb.append(",\tprofit=");
        } else {
            sb.append(",\tloss=");
        }

        sb.append(roundTo5Paise(order.getProfit())).append("}");

        return sb.toString();
    }

    private static String formatTickBasedActiveOrderLog(TickBasedActiveOrder order) {
        final StringBuilder sb = new StringBuilder(ESTIMATED_BUFFER_SIZE);
        sb.append("OrderLog{")
                .append("index='").append(order.getIndex()).append("'")
                .append(",\ttag=").append(order.getTag())
                .append(",\tentry=").append(getStringDateTime(order.getDate()))
                .append(",\texit=").append(order.getExitTimeStamp())
                .append(",\tbuy=").append(roundTo5Paise(order.getBuyPrice()))
                .append(",\ttarget=").append(roundTo5Paise(order.getTarget()))
                .append(",\tsell=").append(roundTo5Paise(order.getSellPrice()))
                .append(",\tcall=").append(order.isCallOrder());

        if (order.getProfit() > 0) {
            sb.append(",\tprofit=");
        } else {
            sb.append(",\tloss=");
        }

        sb.append(roundTo5Paise(order.getProfit())).append("}");

        return sb.toString();
    }

    private static String formatOptionBasedActiveOrderLog(OptionBasedActiveOrder order) {
        final StringBuilder sb = new StringBuilder(ESTIMATED_BUFFER_SIZE);
        sb.append("OrderLog{")
                .append("index='").append(order.getIndex()).append("'")
                .append(",\ttag=").append(order.getTag())
                .append(",\tentry=").append(getStringDateTime(order.getDate()))
                .append(",\texit=").append(order.getExitTimeStamp())
                .append(",\tbuy=").append(roundTo5Paise(order.getBuyPrice()))
                .append(",\ttarget=").append(roundTo5Paise(order.getTarget()))
                .append(",\tsell=").append(roundTo5Paise(order.getSellPrice()));

        if (order.getProfit() > 0) {
            sb.append(",\tprofit=");
        } else {
            sb.append(",\tloss=");
        }

        sb.append(roundTo5Paise(order.getProfit())).append("}");

        return sb.toString();
    }
    // CPD-ON
}
