package com.vish.fno.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.vish.fno.model.Candle;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.AvoidCatchingGenericException"})
public final class FileUtils implements Constants {

    private static final String CANDLESTICK_PATH = "data";
    private static final String ORDER_LOG_FOLDER = "orderLog";

    private final ObjectMapper indentedMapper;
    private final ObjectMapper mapper;
    String filePath = Paths.get(".").normalize().toAbsolutePath() + "\\" + directory + "\\";
    String tickPath = Paths.get(".").normalize().toAbsolutePath() + "\\" + tick_directory + "\\";
    int bufferLength;

    public FileUtils() {
        mapper = new ObjectMapper();
        indentedMapper = new ObjectMapper();
        indentedMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        indentedMapper.enable(SerializationFeature.INDENT_OUTPUT);
        bufferLength = 0;
        createDirectoryIfNotExist(filePath);
    }

    public void saveCandlestickData(List<Candle> candles, String symbol, String date) {
        String path = String.format("%s/%s_%s.json", CANDLESTICK_PATH, symbol, date);
        try {
            createDirectoryIfNotExist(CANDLESTICK_PATH);
            indentedMapper.writeValue(new File(path), candles);
            log.trace("Saved Candlestick data to {}", path);
        } catch (IOException e) {
            log.error("Failed to save candlestick data", e);
        }
    }

    public void createDirectoryIfNotExist(String path) {
        try {
            Files.createDirectories(Paths.get(path));
        } catch (IOException e) {
            log.error("Failed to create directory to path : {}", path, e);
        }
    }

    public void saveTickData(String symbol, Object tick) {
        String path = filePath + getFormattedDate(new Date()) + symbol + ".txt";
        createDirectoryIfNotExist(path);
        try {
            indentedMapper.writeValue(new File(path), tick);
        } catch (IOException e) {
            log.warn("Failed to persist tick value", e);
        }
    }

    public void appendTickToFile(String symbol, Object tick) {
        String folderPath = tickPath + getFormattedDate(new Date());
        createDirectoryIfNotExist(folderPath);
        String filePath = folderPath + "//" + symbol + ".txt";
        filePath = filePath.replaceAll("\\s", "_");

        try {
            String jsonString = mapper.writeValueAsString(tick);
            appendToFile(jsonString, filePath);
        } catch (IOException e) {
            log.warn("Failed to append tick to file", e);
        }
    }

    private void appendToFile(String content, String filePath) throws IOException {
        try (FileWriter fileWriter = new FileWriter(filePath, true);
             BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
             PrintWriter out = new PrintWriter(bufferedWriter)) {
            out.println(content);
        }
    }

    private String candleFileName(String instrument, Date fromDate) {
        createDirectoryIfNotExist(filePath + getFormattedDate(fromDate));
        return filePath + getFormattedDate(fromDate) + "\\" + instrument + ".json";
    }

    private String getFormattedDate(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(date);
    }

    public void logCompletedOrder(ActiveOrder order) {
        try {
            createDirectoryIfNotExist(ORDER_LOG_FOLDER);
            String fileName = String.format("%s/%s-%s-%s-%d.json",
                    ORDER_LOG_FOLDER,
                    order.getTag(),
                    order.getIndex(),
                    TimeUtils.getTodayDate(),
                    (System.currentTimeMillis() % 100_000));
            try (FileWriter fileWriter = new FileWriter(fileName, true);
                 BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
                 PrintWriter out = new PrintWriter(bufferedWriter)) {
                out.println(indentedMapper.writeValueAsString(order));
            }
        } catch (Exception e) {
            try {
                log.error("Failed to persist order log : {}", indentedMapper.writeValueAsString(order), e);
            } catch (JsonProcessingException ex) {
                log.error("Failed to convert order log to json.", e);
            }
        }
    }
}
