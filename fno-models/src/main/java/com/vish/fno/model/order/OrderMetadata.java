package com.vish.fno.model.order;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Metadata attached to each order by the strategy at creation time.
 *
 * <p>Contains signal data computed by the strategy from StrategyContext.
 * This data flows into orderLog JSON for post-hoc analysis without
 * any filtering — BuyOrderExecutor decides real/mock based on these values.
 *
 * <p>Signal fields (populated by strategy, analyzed from orderLog):
 * <ul>
 *   <li>{@code npfAligned} — true if NPF sentiment matches trade direction</li>
 *   <li>{@code session} — current session phase at entry time</li>
 *   <li>{@code bookPressureAligned} — true if book pressure matches direction</li>
 *   <li>{@code maxPainAligned} — true if max pain bias matches direction</li>
 * </ul>
 */
@Getter
@Builder
public class OrderMetadata {
    private final int maxHoldDuration;
    private final String subSignal;
    @Setter
    private String skipReason;

    // Signal data — populated by strategy via SignalFilters.populate(), logged in orderLog for analysis.
    // BuyOrderExecutor reads these to decide real/mock. Post-hoc analysis determines filter value.
    private final Boolean npfAligned;
    private final String npfSentiment;
    private final String session;
    private final Boolean bookPressureAligned;
    private final String bookPressure;
    private final Boolean maxPainAligned;
    private final String flowDirection;
}
