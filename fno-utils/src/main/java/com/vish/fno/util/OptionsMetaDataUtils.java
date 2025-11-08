package com.vish.fno.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static com.vish.fno.util.Constants.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OptionsMetaDataUtils {

    public static int getLotSize(String symbol) {
        return switch (symbol) {
            case NIFTY_BANK -> 15;
            case NIFTY_50 -> 25;
            case BAJFINANCE -> 125;
            case HDFCBANK -> 550;
            case HINDUNILVR -> 300;
            case RELIANCE -> 250;
            default -> 1;
        };
    }
}
