package com.vish.fno.model.wyckoff;

import lombok.Getter;

@Getter
public enum WyckoffPhase {
    ACCUMULATION_PHASE_A("Accumulation Phase A", "Stopping the Prior Downtrend - PS, SC, AR, ST"),
    ACCUMULATION_PHASE_B("Accumulation Phase B", "Building a Cause - Testing supply and demand"),
    ACCUMULATION_PHASE_C("Accumulation Phase C", "Spring/Shakeout - Testing support levels"),
    ACCUMULATION_PHASE_D("Accumulation Phase D", "Sign of Strength - Breaking resistance"),

    MARKUP("Markup", "Uptrend - Higher highs and higher lows"),

    DISTRIBUTION_PHASE_A("Distribution Phase A", "Stopping the Prior Uptrend - PSY, BC, AR, ST"),
    DISTRIBUTION_PHASE_B("Distribution Phase B", "Building a Cause - Testing demand and supply"),
    DISTRIBUTION_PHASE_C("Distribution Phase C", "Upthrust - Testing resistance levels"),
    DISTRIBUTION_PHASE_D("Distribution Phase D", "Sign of Weakness - Breaking support"),

    MARKDOWN("Markdown", "Downtrend - Lower highs and lower lows"),

    REACCUMULATION("Reaccumulation", "Continuation pattern in an uptrend"),
    REDISTRIBUTION("Redistribution", "Continuation pattern in a downtrend"),
    CONSOLIDATION("Consolidation", "Sideways movement - Range bound"),
    UNKNOWN("Unknown", "Unable to determine phase");

    private final String phaseName;
    private final String description;

    WyckoffPhase(String phaseName, String description) {
        this.phaseName = phaseName;
        this.description = description;
    }

    public boolean isAccumulation() {
        return name().startsWith("ACCUMULATION");
    }

    public boolean isDistribution() {
        return name().startsWith("DISTRIBUTION");
    }

    public boolean isMarkup() {
        return this == MARKUP || this == ACCUMULATION_PHASE_D;
    }

    public boolean isMarkdown() {
        return this == MARKDOWN || this == DISTRIBUTION_PHASE_D;
    }
}
