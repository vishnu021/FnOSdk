//package com.vish.fno.strategy.util;
//
//import com.vish.fno.model.Candle;
//import com.vish.fno.util.helper.DataCache;
//import com.vish.fno.util.helper.TimeProvider;
//import lombok.extern.slf4j.Slf4j;
//
//import java.util.List;
//import java.util.Optional;
//
//import static com.vish.fno.util.Constants.NIFTY_50;
//import static com.vish.fno.util.Constants.NIFTY_BANK;
//
//@Slf4j
//public class PCRUtils {
//
//    public void calculatePCR(TimeProvider timeProvider, KiteService kiteService, DataCache dataCache) {
//        if(timeProvider.currentTimeStampIndex() % 5 != 0) {
//            return;
//        }
//
//        try {
//            getCandleData(NIFTY_50);
//            getCandleData(NIFTY_BANK);
//        } catch (Exception e) {
//            log.error("Error while calculating PCR", e);
//        }
//    }
//
//
//    private void getCandleData(final String index) {
//        final Optional<Candle> latestIndex = getLatestCandle(index);
//        if(latestIndex.isEmpty()) {
//            log.warn("latest index candle is empty, is it a holiday ?");
//            return;
//        }
//        final double lastClosePrice = latestIndex.get().close();
//        final List<String> callITMSymbols = kiteService.getITMCallOptionSymbolsAroundPrice(index, lastClosePrice);
//        final List<String> callOTMSymbols = kiteService.getOTMCallOptionSymbolsAroundPrice(index, lastClosePrice);
//
//        final List<String> putITMSymbols = kiteService.getITMPutOptionSymbolsAroundPrice(index, lastClosePrice);
//        final List<String> putOTMSymbols = kiteService.getOTMPutOptionSymbolsAroundPrice(index, lastClosePrice);
//
//        long callITMOI = 0;
//        long callITMVolume = 0;
//        long putITMOI = 0;
//        long putITMVolume = 0;
//        long callOTMOI = 0;
//        long callOTMVolume = 0;
//        long putOTMOI = 0;
//        long putOTMVolume = 0;
//
//        for(String symbol: callITMSymbols) {
//            final Optional<Candle> latestCandleOptional = getLatestCandle(symbol);
//            if(latestCandleOptional.isPresent()) {
//                final Candle latestCandle = latestCandleOptional.get();
//                callITMOI += latestCandle.oi();
//                callITMVolume += latestCandle.volume();
//            }
//        }
//
//        log.info("{} -> call ITM oi : {}, volume: {}", index, callITMOI, callITMVolume);
//
//        for(String symbol: putITMSymbols) {
//            final Optional<Candle> latestCandleOptional = getLatestCandle(symbol);
//            if(latestCandleOptional.isPresent()) {
//                final Candle latestCandle = latestCandleOptional.get();
//                putITMOI += latestCandle.oi();
//                putITMVolume += latestCandle.volume();
//            }
//        }
//        log.info("{} -> put ITM oi : {}, volume: {}", index, callITMOI, callITMVolume);
//
//        for(String symbol: callOTMSymbols) {
//            final Optional<Candle> latestCandleOptional = getLatestCandle(symbol);
//            if(latestCandleOptional.isPresent()) {
//                final Candle latestCandle = latestCandleOptional.get();
//                callOTMOI += latestCandle.oi();
//                callOTMVolume += latestCandle.volume();
//            }
//        }
//
//        log.info("{} -> call OTM oi : {}, volume: {}", index, callOTMOI, callOTMVolume);
//
//        for(String symbol: putOTMSymbols) {
//            final Optional<Candle> latestCandleOptional = getLatestCandle(symbol);
//            if(latestCandleOptional.isPresent()) {
//                final Candle latestCandle = latestCandleOptional.get();
//                putOTMOI += latestCandle.oi();
//                putOTMVolume += latestCandle.volume();
//            }
//        }
//        log.info("{} -> put OTM oi : {}, volume: {}", index, putOTMOI, putOTMVolume);
//
//        long callOI = callOTMOI + callITMOI;
//        long callVolume = callITMVolume + callITMVolume;
//        long putOI = putOTMOI + putITMOI;
//        long putVolume = putITMVolume + putITMVolume;
//
//        log.info("{} -> call oi : {}, volume: {}", index, callOI, callVolume);
//        log.info("{} -> put  oi : {}, volume: {}", index, putOI, putVolume);
//        log.info("{} -> PCR: {}", index, ((double)putOI)/callOI);
//    }
//
//    private Optional<Candle> getLatestCandle(String symbol) {
//        final List<Candle> symbolCandles = dataCacheImpl.updateAndGetMinuteData(symbol);
//        if(symbolCandles == null || symbolCandles.isEmpty()) {
//            return Optional.empty();
//        }
//        return Optional.of(symbolCandles.get(symbolCandles.size() - 1));
//    }
//
//
//}
