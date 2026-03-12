package com.vish.fno.model;

/**
 * Indian stock exchanges and derivative segments.
 */
public enum Exchange {
    NSE("NSE"),
    NFO("NFO"),
    BFO("BFO"),
    BSE("BSE");

    private final String code;

    Exchange(String code) {
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
