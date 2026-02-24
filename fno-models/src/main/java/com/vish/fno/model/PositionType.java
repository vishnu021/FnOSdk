package com.vish.fno.model;

/**
 * Kite Connect position / account types.
 */
public enum PositionType {
    EQUITY("equity"),
    NET("net"),
    DAY("day");

    private final String code;

    PositionType(String code) {
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
