package com.vish.fno.phase.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vish.fno.model.Candle;
import com.vish.fno.model.wyckoff.IWyckoffPhaseIdentifier;
import com.vish.fno.model.wyckoff.WyckoffPhase;
import com.vish.fno.phase.factory.WyckoffPhaseIdentifierFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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


public class WyckoffHourlyAnalysisService {
    
    private static final Logger logger = LoggerFactory.getLogger(WyckoffHourlyAnalysisService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final WyckoffPhaseService wyckoffPhaseService;
    private final WyckoffPhaseIdentifierFactory identifierFactory;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final IWyckoffPhaseIdentifier phaseIdentifier;

    public WyckoffHourlyAnalysisService(WyckoffPhaseService wyckoffPhaseService,
                                       WyckoffPhaseIdentifierFactory identifierFactory,
                                       ObjectMapper objectMapper,
                                       ResourceLoader resourceLoader) {
        this.wyckoffPhaseService = wyckoffPhaseService;
        this.identifierFactory = identifierFactory;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.phaseIdentifier = identifierFactory.getDefault();

        logger.info("WyckoffHourlyAnalysisService initialized with identifier: {}",
                   phaseIdentifier.getIdentifierType());
    }
    
    public Map<LocalDateTime, WyckoffPhase> analyzeHourlyPhases(String symbol, LocalDate startDate, LocalDate endDate) throws IOException {
        return analyzeHourlyPhases(symbol, startDate, endDate, "simulation-results/analysis");
    }
    
    public Map<LocalDateTime, WyckoffPhase> analyzeHourlyPhases(String symbol, LocalDate startDate, LocalDate endDate, String outputPath) throws IOException {
        logger.info("Starting hourly Wyckoff phase analysis for symbol: {} from {} to {}", symbol, startDate, endDate);
        
        Map<LocalDateTime, WyckoffPhase> hourlyPhases = new TreeMap<>();
        List<Candle> allData = loadDataForDateRange(symbol, startDate, endDate);
        
        if (allData.isEmpty()) {
            logger.warn("No data found for symbol: {}", symbol);
            return hourlyPhases;
        }
        
        // Group data by hour
        Map<LocalDateTime, List<Candle>> hourlyData = groupDataByHour(allData);
        
        // Create hourly candles
        List<Candle> hourlyCandles = createHourlyCandles(hourlyData);
        
        // Analyze each hour
        for (int i = 0; i < hourlyCandles.size(); i++) {
            Candle hourlyCandle = hourlyCandles.get(i);
            LocalDateTime hourTime = ZonedDateTime.parse(hourlyCandle.time(), TIME_FORMATTER).toLocalDateTime();
            
            WyckoffPhase phase = phaseIdentifier.identifyPhase(hourlyCandles, i);
            hourlyPhases.put(hourTime, phase);
        }
        
        // Export detailed results
        exportHourlyAnalysisResults(symbol, hourlyPhases, startDate, endDate, outputPath);
        printHourlyAnalysisSummary(symbol, hourlyPhases);
        
        return hourlyPhases;
    }
    
    private List<Candle> loadDataForDateRange(String symbol, LocalDate startDate, LocalDate endDate) throws IOException {
        List<Candle> allData = new ArrayList<>();
        
        // Load August data
        String[] dataFiles = {
            "NIFTY 50_2025-08-01.json"
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
                    
                    // Filter data for the specified date range
                    Arrays.stream(monthData)
                        .filter(candle -> {
                            LocalDate candleDate = ZonedDateTime.parse(candle.time(), TIME_FORMATTER).toLocalDate();
                            return !candleDate.isBefore(startDate) && !candleDate.isAfter(endDate);
                        })
                        .forEach(allData::add);
                    
                    logger.info("Loaded {} candles from {} for date range", 
                        allData.size(), fileName);
                }
            } catch (IOException e) {
                logger.error("Error loading file: {}", fileName, e);
            }
        }
        
        allData.sort(Comparator.comparing(c -> ZonedDateTime.parse(c.time(), TIME_FORMATTER)));
        return allData;
    }
    
    private Map<LocalDateTime, List<Candle>> groupDataByHour(List<Candle> data) {
        return data.stream()
            .collect(Collectors.groupingBy(
                candle -> {
                    LocalDateTime dt = ZonedDateTime.parse(candle.time(), TIME_FORMATTER).toLocalDateTime();
                    // Round down to hour
                    return dt.withMinute(0).withSecond(0).withNano(0);
                },
                TreeMap::new,
                Collectors.toList()
            ));
    }
    
    private List<Candle> createHourlyCandles(Map<LocalDateTime, List<Candle>> hourlyData) {
        List<Candle> hourlyCandles = new ArrayList<>();
        
        for (Map.Entry<LocalDateTime, List<Candle>> entry : hourlyData.entrySet()) {
            List<Candle> hourCandles = entry.getValue();
            if (hourCandles.isEmpty()) continue;
            
            hourCandles.sort(Comparator.comparing(c -> ZonedDateTime.parse(c.time(), TIME_FORMATTER)));
            
            double open = hourCandles.get(0).open();
            double close = hourCandles.get(hourCandles.size() - 1).close();
            double high = hourCandles.stream().mapToDouble(Candle::high).max().orElse(0);
            double low = hourCandles.stream().mapToDouble(Candle::low).min().orElse(0);
            long volume = hourCandles.stream().mapToLong(Candle::volume).sum();
            long oi = hourCandles.get(hourCandles.size() - 1).oi();
            
            // Use the first candle's time for the hourly candle
            String hourTime = hourCandles.get(0).time();
            
            Candle hourlyCandle = new Candle(
                hourTime, open, high, low, close, volume, oi
            );
            
            hourlyCandles.add(hourlyCandle);
        }
        
        hourlyCandles.sort(Comparator.comparing(Candle::time));
        return hourlyCandles;
    }
    
    private void exportHourlyAnalysisResults(String symbol, Map<LocalDateTime, WyckoffPhase> hourlyPhases, 
                                            LocalDate startDate, LocalDate endDate, String outputPath) {
        // Ensure the directory exists
        File directory = new File(outputPath);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        
        String fileName = String.format("%s/wyckoff-hourly-analysis_%s_%s_to_%s.csv", 
            outputPath, 
            symbol.replace(" ", "_"), 
            startDate.format(DATE_FORMATTER),
            endDate.format(DATE_FORMATTER)
        );
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(fileName))) {
            writer.println("Date,Hour,Phase,Phase Name,Description");
            
            LocalDate currentDate = null;
            for (Map.Entry<LocalDateTime, WyckoffPhase> entry : hourlyPhases.entrySet()) {
                LocalDateTime dateTime = entry.getKey();
                WyckoffPhase phase = entry.getValue();
                
                // Add date separator for readability
                if (currentDate == null || !dateTime.toLocalDate().equals(currentDate)) {
                    currentDate = dateTime.toLocalDate();
                    writer.println();
                    writer.println("# " + currentDate.format(DATE_FORMATTER));
                }
                
                writer.printf("%s,%s,%s,\"%s\",\"%s\"%n",
                    dateTime.toLocalDate().format(DATE_FORMATTER),
                    dateTime.format(HOUR_FORMATTER),
                    phase.name(),
                    phase.getPhaseName(),
                    phase.getDescription()
                );
            }
            
            logger.info("Hourly analysis results exported to: {}", fileName);
        } catch (IOException e) {
            logger.error("Error exporting hourly results", e);
        }
    }
    
    private void printHourlyAnalysisSummary(String symbol, Map<LocalDateTime, WyckoffPhase> hourlyPhases) {
        logger.info("=== Hourly Wyckoff Analysis Summary for {} ===", symbol);
        
        // Group by date
        Map<LocalDate, List<Map.Entry<LocalDateTime, WyckoffPhase>>> dailyPhases = 
            hourlyPhases.entrySet().stream()
                .collect(Collectors.groupingBy(
                    entry -> entry.getKey().toLocalDate(),
                    TreeMap::new,
                    Collectors.toList()
                ));
        
        // Print daily summaries
        for (Map.Entry<LocalDate, List<Map.Entry<LocalDateTime, WyckoffPhase>>> dayEntry : dailyPhases.entrySet()) {
            LocalDate date = dayEntry.getKey();
            List<Map.Entry<LocalDateTime, WyckoffPhase>> dayPhases = dayEntry.getValue();
            
            logger.info("\n=== {} ===", date.format(DATE_FORMATTER));
            
            // Count phases for the day
            Map<WyckoffPhase, Long> phaseCounts = dayPhases.stream()
                .collect(Collectors.groupingBy(
                    Map.Entry::getValue,
                    Collectors.counting()
                ));
            
            // Identify dominant phases and transitions
            WyckoffPhase openPhase = dayPhases.get(0).getValue();
            WyckoffPhase closePhase = dayPhases.get(dayPhases.size() - 1).getValue();
            
            logger.info("Market Open (09:15): {}", openPhase.getPhaseName());
            logger.info("Market Close (15:30): {}", closePhase.getPhaseName());
            
            // Print hourly progression
            logger.info("Hourly Progression:");
            for (Map.Entry<LocalDateTime, WyckoffPhase> hourEntry : dayPhases) {
                LocalDateTime hour = hourEntry.getKey();
                WyckoffPhase phase = hourEntry.getValue();
                logger.info("  {} - {}", hour.format(HOUR_FORMATTER), phase.getPhaseName());
            }
            
            // Identify key transitions
            WyckoffPhase previousPhase = null;
            for (Map.Entry<LocalDateTime, WyckoffPhase> hourEntry : dayPhases) {
                WyckoffPhase currentPhase = hourEntry.getValue();
                if (previousPhase != null && !currentPhase.equals(previousPhase)) {
                    if (isSignificantTransition(previousPhase, currentPhase)) {
                        logger.info("  KEY TRANSITION at {}: {} → {}", 
                            hourEntry.getKey().format(HOUR_FORMATTER),
                            previousPhase.getPhaseName(),
                            currentPhase.getPhaseName());
                    }
                }
                previousPhase = currentPhase;
            }
            
            // Daily phase distribution
            logger.info("Phase Distribution for {}:", date);
            phaseCounts.entrySet().stream()
                .sorted(Map.Entry.<WyckoffPhase, Long>comparingByValue().reversed())
                .forEach(entry -> 
                    logger.info("  {} - {} hours", 
                        entry.getKey().getPhaseName(),
                        entry.getValue())
                );
        }
    }
    
    private boolean isSignificantTransition(WyckoffPhase from, WyckoffPhase to) {
        // Markup to Markdown or vice versa
        if ((from == WyckoffPhase.MARKUP && to == WyckoffPhase.MARKDOWN) ||
            (from == WyckoffPhase.MARKDOWN && to == WyckoffPhase.MARKUP)) {
            return true;
        }
        
        // Consolidation to trending
        if (from == WyckoffPhase.CONSOLIDATION && 
            (to == WyckoffPhase.MARKUP || to == WyckoffPhase.MARKDOWN)) {
            return true;
        }
        
        // Trending to consolidation
        if ((from == WyckoffPhase.MARKUP || from == WyckoffPhase.MARKDOWN) && 
            to == WyckoffPhase.CONSOLIDATION) {
            return true;
        }
        
        // Accumulation/Distribution phase transitions
        if (from.isAccumulation() && to.isMarkup()) {
            return true;
        }
        if (from.isDistribution() && to.isMarkdown()) {
            return true;
        }
        
        return false;
    }
}