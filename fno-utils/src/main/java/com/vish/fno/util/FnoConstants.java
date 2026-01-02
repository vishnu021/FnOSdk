package com.vish.fno.util;

import java.util.Map;

@SuppressWarnings("PMD.ConstantsInInterface")
public interface FnoConstants {

    // Exchange Names
    String NSE = "NSE";
    String NFO = "NFO";
    String BFO = "BFO";
    String BSE = "BSE";

    // Instrument Types
    String CE = "CE";
    String PE = "PE";
    String FUT = "FUT";

    // Order Transaction Types
    String BUY = "BUY";
    String SELL = "SELL";

    // Order Status
    String COMPLETE = "COMPLETE";

    // Order Metadata Keys
    String KITE_ORDER_ID = "kiteOrderId";

    // Account/Position Types
    String EQUITY = "equity";
    String NET = "net";
    String DAY = "day";

    // Index Names (as displayed in NSE)
    String NIFTY_BANK = "NIFTY BANK";
    String NIFTY_50 = "NIFTY 50";
    String NIFTY_FIN_SERVICE = "NIFTY FIN SERVICE";
    String NIFTY_MIDCAP_SELECT = "NIFTY MIDCAP SELECT";
    String BANKEX = "BANKEX";
    String SENSEX = "SENSEX";

    // Derivative Symbol Names (as used in F&O contracts)
    String DERIVATIVE_NIFTY = "NIFTY";
    String DERIVATIVE_BANKNIFTY = "BANKNIFTY";
    String DERIVATIVE_FINNIFTY = "FINNIFTY";
    String DERIVATIVE_MIDCPNIFTY = "MIDCPNIFTY";
    String DERIVATIVE_BANKEX = "BANKEX";
    String DERIVATIVE_SENSEX = "SENSEX";

    /**
     * Mapping from index names (as displayed in NSE) to their derivative trading symbols.
     * This handles the mismatch in Indian Stock Market where index names differ from
     * their futures/options trading symbols.
     *
     * <p>Example: "NIFTY 50" index trades as "NIFTY" in F&O segment
     */
    Map<String, String> INDEX_TO_DERIVATIVE = Map.of(
            NIFTY_50, DERIVATIVE_NIFTY,
            NIFTY_BANK, DERIVATIVE_BANKNIFTY,
            NIFTY_FIN_SERVICE, DERIVATIVE_FINNIFTY,
            NIFTY_MIDCAP_SELECT, DERIVATIVE_MIDCPNIFTY,
            BANKEX, DERIVATIVE_BANKEX,
            SENSEX, DERIVATIVE_SENSEX
    );

    String BAJFINANCE = "BAJFINANCE";
    String HDFCBANK = "HDFCBANK";
    String HINDUNILVR = "HINDUNILVR";
    String RELIANCE = "RELIANCE";
    String directory = "instrument_cache";
    String tick_directory = "tick";
    String MINUTE = "minute";
    String STOCK_PRICE = "Stock Price";
    String CANDLESTICK = "candlestick";
    String VOLUME = "Volume";
    String LINE = "line";
    String BAR = "bar";

    String DATE_TIME_SEC_T_FORMAT = "yyyy-MM-dd'T'HH:mm:ssX";
    String DATE_TIME_MS_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
    String DATE_TIME_SEC_FORMAT = "yyyy-MM-dd HH:mm:ss";
    String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm";
    String DATE_FORMAT = "yyyy-MM-dd";
    String TIME_FORMAT = "HH:mm";
    String YEAR_FORMAT = "yy";
}
