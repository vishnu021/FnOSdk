package com.vish.fno.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OptionUtils {

    public static int getQuantity(String symbol) {
        return switch (symbol) {
            case "NIFTY BANK" -> 15;
            case "NIFTY 50" -> 50;
            case "BAJFINANCE" -> 125;
            case "HDFCBANK" -> 550;
            case "HINDUNILVR" -> 300;
            case "RELIANCE" -> 250;
            default -> 0;
        };
    }
}
