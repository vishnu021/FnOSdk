package com.vish.fno.model.order;

public enum OrderSellReason {
    TARGET_HIT,
    STOP_LOSS_HIT,
    EXPIRY_TIME_REACHED,
    MAX_HOLD_DURATION_REACHED,
    /**
     * Flag-gated time-scratch exit (OAV2 ADR-0063): position aged past the scratch window still
     * inside the flat band. Deliberately distinct from {@link #STOP_LOSS_HIT} so scratch exits
     * never feed stop-loss-streak accounting (same-strike cooldown / loss-streak rails).
     */
    SCRATCH_TIME_EXIT
}
