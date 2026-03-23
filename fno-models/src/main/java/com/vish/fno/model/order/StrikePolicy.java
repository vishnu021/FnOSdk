package com.vish.fno.model.order;

/**
 * Strike selection policy for option entry. Controls how many strike intervals
 * away from ATM the option entry strike is chosen.
 *
 * <p>ATM = nearest strike to current index price (round half-up).
 * ITM = in-the-money (lower strikes for CE, higher strikes for PE).
 * OTM = out-of-the-money (higher strikes for CE, lower strikes for PE).
 *
 * <p>Formula: targetStrike = ATM + (isCall ? -1 : +1) * offset * strikeInterval
 */
public enum StrikePolicy {
    OTM_2(-2),
    OTM_1(-1),
    ATM(0),
    ITM_1(1),
    ITM_2(2);

    private final int offset;

    StrikePolicy(int offset) {
        this.offset = offset;
    }

    public int getOffset() {
        return offset;
    }
}
