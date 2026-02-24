package com.vish.fno.model;

/**
 * F&amp;O instrument types used in NSE/BSE derivative segments.
 */
public enum InstrumentType {
    CE("CE"),
    PE("PE"),
    FUT("FUT");

    private final String code;

    InstrumentType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * Case-insensitive match against a raw string value.
     *
     * @param value the string to compare (e.g., from Kite API)
     * @return true if this enum's code matches the given value
     */
    public boolean matches(String value) {
        return code.equalsIgnoreCase(value);
    }

    @Override
    public String toString() {
        return code;
    }
}
