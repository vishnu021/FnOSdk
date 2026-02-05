package com.vish.fno.reader.core;

import com.vish.fno.model.helper.ITMResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * KiteITMResolver - Production implementation of ITMResolver using KiteService.
 *
 * This resolver delegates to KiteService for live option symbol resolution:
 * - Uses kiteService.getITMStock() for ITM symbol resolution
 * - Uses kiteService.getOTMStock() for OTM symbol resolution
 * - Calls kiteService.appendIndexITMOptions() to prepare/refresh symbol mappings
 *
 * Usage:
 * <pre>
 * ITMResolver resolver = new KiteITMResolver(kiteService);
 * StrategyExecutor executor = new StrategyExecutor(..., resolver, ...);
 * </pre>
 *
 * @see ITMResolver for interface documentation
 */
@Slf4j
@RequiredArgsConstructor
public class KiteITMResolver implements ITMResolver {

    private final KiteService kiteService;

    @Override
    public String resolveITMSymbol(String index, double price, boolean isCall) {
        String symbol = kiteService.getITMStock(index, price, isCall);
        log.debug("Resolved ITM {} for {} at price {}: {}", isCall ? "CALL" : "PUT", index, price, symbol);
        return symbol;
    }

    @Override
    public String resolveOTMSymbol(String index, double price, boolean isCall) {
        String symbol = kiteService.getOTMStock(index, price, isCall);
        log.debug("Resolved OTM {} for {} at price {}: {}", isCall ? "CALL" : "PUT", index, price, symbol);
        return symbol;
    }

    @Override
    public void prepareSymbols() {
        kiteService.appendIndexITMOptions();
    }
}
