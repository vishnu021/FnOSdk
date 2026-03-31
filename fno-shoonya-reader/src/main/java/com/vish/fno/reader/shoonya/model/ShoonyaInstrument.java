package com.vish.fno.reader.shoonya.model;

/**
 * Instrument parsed from Shoonya contract master CSV.
 *
 * <p>CSV columns: Exchange, Token, LotSize, Symbol, TradingSymbol, Expiry, Instrument, OptionType, StrikePrice, TickSize
 *
 * @param exchange        exchange code (NSE, NFO, BSE, BFO)
 * @param token           numeric instrument token
 * @param lotSize         contract lot size
 * @param symbol          underlying name (e.g., "NIFTY", "RELIANCE")
 * @param tradingSymbol   full trading symbol (e.g., "NIFTY28APR26C17200")
 * @param expiry          expiry date string (e.g., "28-APR-2026"), null for cash
 * @param instrumentType  instrument type (OPTIDX, FUTIDX, FUTSTK, OPTSTK, EQ, INDEX)
 * @param optionType      option type (CE, PE, XX), null for cash
 * @param strikePrice     strike price (-0.01 for futures), 0 for cash
 * @param tickSize        minimum price movement
 */
public record ShoonyaInstrument(
    String exchange,
    long token,
    int lotSize,
    String symbol,
    String tradingSymbol,
    String expiry,
    String instrumentType,
    String optionType,
    double strikePrice,
    double tickSize
) {
}
