package com.vish.fno.reader.shoonya.core;

import com.vish.fno.model.helper.ITMResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ShoonyaITMResolver implements ITMResolver {

    private final ShoonyaService shoonyaService;

    @Override
    public String resolveITMSymbol(String index, double price, boolean isCall) {
        String symbol = shoonyaService.getITMStock(index, price, isCall);
        log.debug("Resolved Shoonya ITM {} for {} at price {}: {}",
                isCall ? "CALL" : "PUT", index, price, symbol);
        return symbol;
    }

    @Override
    public String resolveOTMSymbol(String index, double price, boolean isCall) {
        String symbol = shoonyaService.getOTMStock(index, price, isCall);
        log.debug("Resolved Shoonya OTM {} for {} at price {}: {}",
                isCall ? "CALL" : "PUT", index, price, symbol);
        return symbol;
    }

    @Override
    public void prepareSymbols() {
        // No WebSocket subscription needed for Shoonya (out of scope)
        // Instrument cache loads lazily on first access
        log.debug("Shoonya prepareSymbols called (no-op: instruments load lazily)");
    }
}
