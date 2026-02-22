package com.vish.fno.util;

import java.time.LocalTime;
import java.util.Map;

@SuppressWarnings("PMD.ConstantsInInterface")
public interface FnoConstants {

    // NSE Market Hours (IST)
    int MARKET_OPEN_HOUR = 9;
    int MARKET_OPEN_MINUTE = 15;
    int MARKET_CLOSE_HOUR = 15;
    int MARKET_CLOSE_MINUTE = 30;
    int TOTAL_TRADING_MINUTES = 375;  // 6 hours 15 minutes (from 9:15 to 15:30)
    int MINUTES_IN_HOUR = 60;

    /** Market opens at 9:15 — first candle starts forming */
    LocalTime MARKET_OPEN_TIME = LocalTime.of(MARKET_OPEN_HOUR, MARKET_OPEN_MINUTE);
    /** Market closes at 15:30 */
    LocalTime MARKET_CLOSE_TIME = LocalTime.of(MARKET_CLOSE_HOUR, MARKET_CLOSE_MINUTE);
    /** Strategy execution starts at 9:16 — after first candle completes */
    LocalTime DEFAULT_STRATEGY_START_TIME = LocalTime.of(9, 16);
    /** Strategy execution ends at 15:30 */
    LocalTime DEFAULT_STRATEGY_END_TIME = MARKET_CLOSE_TIME;

    // Exchange Names
    String NSE = "NSE";
    String NFO = "NFO";
    String BFO = "BFO";
    String BSE = "BSE";

    // Instrument Types
    String CE = "CE";
    String PE = "PE";
    String FUT = "FUT";

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

    String directory = "instrument_cache";
    String tick_directory = "tick";
    String MINUTE = "minute";

    String DATE_TIME_SEC_T_FORMAT = "yyyy-MM-dd'T'HH:mm:ssX";
    String DATE_TIME_MS_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
    String DATE_TIME_SEC_FORMAT = "yyyy-MM-dd HH:mm:ss";
    String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm";
    String DATE_FORMAT = "yyyy-MM-dd";
    String TIME_FORMAT = "HH:mm";
    String YEAR_FORMAT = "yy";
}
