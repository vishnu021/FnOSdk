package com.vish.fno.phase.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vish.fno.model.Candle;
import com.vish.fno.model.wyckoff.IWyckoffPhaseIdentifier;
import com.vish.fno.model.wyckoff.WyckoffPhase;
import com.vish.fno.phase.factory.WyckoffPhaseIdentifierFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;


public class WyckoffAnalysisService {
    
    private static final Logger logger = LoggerFactory.getLogger(WyckoffAnalysisService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    private final WyckoffPhaseService wyckoffPhaseService;
    private final WyckoffPhaseIdentifierFactory identifierFactory;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final IWyckoffPhaseIdentifier phaseIdentifier;

    public WyckoffAnalysisService(WyckoffPhaseService wyckoffPhaseService,
                                 WyckoffPhaseIdentifierFactory identifierFactory,
                                 ObjectMapper objectMapper,
                                 ResourceLoader resourceLoader) {
        this.wyckoffPhaseService = wyckoffPhaseService;
        this.identifierFactory = identifierFactory;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.phaseIdentifier = identifierFactory.getDefault();

        logger.info("WyckoffAnalysisService initialized with identifier: {}",
                   phaseIdentifier.getIdentifierType());
    }
    
    public Map<LocalDate, WyckoffPhase> analyzeSymbol(String symbol) throws IOException {
        return analyzeSymbol(symbol, null, null, "simulation-results/analysis");
    }
    
    public Map<LocalDate, WyckoffPhase> analyzeSymbol(String symbol, LocalDate startDate, LocalDate endDate, String outputPath) throws IOException {
        logger.info("Starting Wyckoff phase analysis for symbol: {}", symbol);
        
        Map<LocalDate, WyckoffPhase> dailyPhases = new TreeMap<>();
        List<Candle> allData = loadAllDataForSymbol(symbol, startDate, endDate);
        
        if (allData.isEmpty()) {
            logger.warn("No data found for symbol: {}", symbol);
            return dailyPhases;
        }
        
        Map<LocalDate, List<Candle>> dailyData = groupDataByDate(allData);
        List<Candle> aggregatedDailyCandles = createDailyCandles(dailyData);
        
        for (int i = 0; i < aggregatedDailyCandles.size(); i++) {
            Candle dailyCandle = aggregatedDailyCandles.get(i);
            LocalDate date = ZonedDateTime.parse(dailyCandle.time(), TIME_FORMATTER).toLocalDate();
            
            WyckoffPhase phase = phaseIdentifier.identifyPhase(aggregatedDailyCandles, i);
            dailyPhases.put(date, phase);
            
            logger.debug("Date: {}, Phase: {}", date, phase.getPhaseName());
        }
        
        exportAnalysisResults(symbol, dailyPhases, outputPath);
        printAnalysisSummary(symbol, dailyPhases);
        
        return dailyPhases;
    }
    
    private List<Candle> loadAllDataForSymbol(String symbol, LocalDate startDate, LocalDate endDate) throws IOException {
        List<Candle> allData = new ArrayList<>();
        
        // Determine which data files to load based on date range
        String[] dataFiles = {
            symbol.replace(" ", " ") + "_2025-06-01.json",
            symbol.replace(" ", " ") + "_2025-07-01.json",
            symbol.replace(" ", " ") + "_2025-08-01.json"
        };
        
        for (String fileName : dataFiles) {
            String resourcePath = "classpath:data/" + fileName;
            try {
                Resource resource = resourceLoader.getResource(resourcePath);
                if (resource.exists()) {
                    Candle[] monthData = objectMapper.readValue(
                        resource.getInputStream(), 
                        Candle[].class
                    );
                    
                    // Filter data based on date range if specified
                    Arrays.stream(monthData)
                        .filter(candle -> {
                            LocalDate candleDate = ZonedDateTime.parse(candle.time(), TIME_FORMATTER).toLocalDate();
                            boolean afterStart = startDate == null || !candleDate.isBefore(startDate);
                            boolean beforeEnd = endDate == null || !candleDate.isAfter(endDate);
                            return afterStart && beforeEnd;
                        })
                        .forEach(allData::add);
                    
                    logger.info("Loaded {} candles from {}", monthData.length, fileName);
                }
            } catch (IOException e) {
                logger.debug("File not found or error loading: {}", fileName);
            }
        }
        
        allData.sort(Comparator.comparing(Candle::time));
        logger.info("Total candles loaded for {}: {} (from {} to {})", 
            symbol, allData.size(), startDate, endDate);
        return allData;
    }
    
    private Map<LocalDate, List<Candle>> groupDataByDate(List<Candle> data) {
        return data.stream()
            .collect(Collectors.groupingBy(
                candle -> ZonedDateTime.parse(candle.time(), TIME_FORMATTER).toLocalDate(),
                TreeMap::new,
                Collectors.toList()
            ));
    }
    
    private List<Candle> createDailyCandles(Map<LocalDate, List<Candle>> dailyData) {
        List<Candle> dailyCandles = new ArrayList<>();
        
        for (Map.Entry<LocalDate, List<Candle>> entry : dailyData.entrySet()) {
            List<Candle> dayCandles = entry.getValue();
            if (dayCandles.isEmpty()) continue;
            
            dayCandles.sort(Comparator.comparing(c -> ZonedDateTime.parse(c.time(), TIME_FORMATTER)));
            
            double open = dayCandles.get(0).open();
            double close = dayCandles.get(dayCandles.size() - 1).close();
            double high = dayCandles.stream().mapToDouble(Candle::high).max().orElse(0);
            double low = dayCandles.stream().mapToDouble(Candle::low).min().orElse(0);
            long volume = dayCandles.stream().mapToLong(Candle::volume).sum();
            long oi = dayCandles.get(dayCandles.size() - 1).oi();
            
            String dayTime = dayCandles.get(0).time();
            
            Candle dailyCandle = new Candle(
                dayTime, open, high, low, close, volume, oi
            );
            
            dailyCandles.add(dailyCandle);
        }
        
        dailyCandles.sort(Comparator.comparing(Candle::time));
        return dailyCandles;
    }
    
    private void exportAnalysisResults(String symbol, Map<LocalDate, WyckoffPhase> dailyPhases, String outputPath) {
        // Ensure the directory exists
        File directory = new File(outputPath);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        
        String fileName = String.format("%s/wyckoff-analysis_%s_%s.csv", 
            outputPath, 
            symbol.replace(" ", "_"), 
            LocalDate.now().format(DATE_FORMATTER)
        );
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(fileName))) {
            writer.println("Date,Phase,Phase Name,Description");
            
            for (Map.Entry<LocalDate, WyckoffPhase> entry : dailyPhases.entrySet()) {
                WyckoffPhase phase = entry.getValue();
                writer.printf("%s,%s,\"%s\",\"%s\"%n",
                    entry.getKey().format(DATE_FORMATTER),
                    phase.name(),
                    phase.getPhaseName(),
                    phase.getDescription()
                );
            }
            
            logger.info("Analysis results exported to: {}", fileName);
        } catch (IOException e) {
            logger.error("Error exporting results", e);
        }
    }
    
    private void printAnalysisSummary(String symbol, Map<LocalDate, WyckoffPhase> dailyPhases) {
        logger.info("=== Wyckoff Analysis Summary for {} ===", symbol);
        
        Map<WyckoffPhase, Long> phaseCounts = dailyPhases.values().stream()
            .collect(Collectors.groupingBy(
                phase -> phase,
                Collectors.counting()
            ));
        
        logger.info("Phase Distribution:");
        phaseCounts.entrySet().stream()
            .sorted(Map.Entry.<WyckoffPhase, Long>comparingByValue().reversed())
            .forEach(entry -> 
                logger.info("  {} - {} days ({}%)", 
                    entry.getKey().getPhaseName(),
                    entry.getValue(),
                    String.format("%.1f", (entry.getValue() * 100.0) / dailyPhases.size())
                )
            );
        
        identifyPhaseTransitions(dailyPhases);
    }
    
    private void identifyPhaseTransitions(Map<LocalDate, WyckoffPhase> dailyPhases) {
        logger.info("\nSignificant Phase Transitions:");
        
        WyckoffPhase previousPhase = null;
        LocalDate previousDate = null;
        
        for (Map.Entry<LocalDate, WyckoffPhase> entry : dailyPhases.entrySet()) {
            WyckoffPhase currentPhase = entry.getValue();
            LocalDate currentDate = entry.getKey();
            
            if (previousPhase != null && !currentPhase.equals(previousPhase)) {
                if (isSignificantTransition(previousPhase, currentPhase)) {
                    logger.info("  {} -> {} on {}", 
                        previousPhase.getPhaseName(),
                        currentPhase.getPhaseName(),
                        currentDate.format(DATE_FORMATTER)
                    );
                }
            }
            
            previousPhase = currentPhase;
            previousDate = currentDate;
        }
    }
    
    private boolean isSignificantTransition(WyckoffPhase from, WyckoffPhase to) {
        if (from == WyckoffPhase.UNKNOWN || to == WyckoffPhase.UNKNOWN) {
            return false;
        }
        
        if (from == WyckoffPhase.ACCUMULATION_PHASE_C && to == WyckoffPhase.ACCUMULATION_PHASE_D) {
            return true;
        }
        if (from == WyckoffPhase.DISTRIBUTION_PHASE_C && to == WyckoffPhase.DISTRIBUTION_PHASE_D) {
            return true;
        }
        if (from.isAccumulation() && to.isMarkup()) {
            return true;
        }
        if (from.isDistribution() && to.isMarkdown()) {
            return true;
        }
        if (from.isAccumulation() && to.isDistribution()) {
            return true;
        }
        if (from.isDistribution() && to.isAccumulation()) {
            return true;
        }
        
        return false;
    }
}