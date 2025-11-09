package com.vish.fno.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vish.fno.model.Candle;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("PMD.AvoidThrowingRawExceptionTypes")
public final class CandleUtils {

    private static final ObjectMapper mapper = new ObjectMapper();
    public static final List<String> timeArray = new ArrayList<>();

    static {
        int hour = 9;
        int minute = 15;
        for (int i = 0; i <= 375; i++) {
            timeArray.add(toTimeValue(hour) + ":" + toTimeValue(minute));
            minute++;
            if (minute == 60) {
                hour++;
                minute = 0;
            }
        }
    }

    public static boolean isBullish(Candle candle) {
        return candle.close() > candle.open();
    }

    public static boolean isBearish(Candle candle) {
        return candle.close() < candle.open();
    }

    public static double getBodyLength(Candle candle) {
        if(isBullish(candle)) {
            return candle.close() - candle.open();
        }
        return candle.open() - candle.close();
    }

    public static double getTotalLength(Candle candle) {
        return candle.high() - candle.low();
    }

    public static double getUpperWick(Candle candle) {
        if(isBullish(candle)) {
            return candle.high() - candle.close();
        }
        return candle.high() - candle.open();
    }

    public static double getLowerWick(Candle candle) {
        if(isBullish(candle)) {
            return candle.open() - candle.low();
        }
        return candle.close() - candle.low();
    }

    private static String toTimeValue(int timeVal) {
        if (timeVal >= 0 && timeVal <= 9) {
            return "0" + timeVal;
        }
        return String.valueOf(timeVal);
    }

    public static List<Candle> getCandleData(String filePath) {
        try {
            String candles = readFile(filePath);
            return mapper.readValue(candles,  new TypeReference<List<Candle>>(){});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Candle> getPrevDayCandleData(String filePath) {
        try {
            String candles = readFile(filePath);
            return mapper.readValue(candles,  new TypeReference<List<Candle>>(){});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Double> getSmaData(String fileName) {
        return getIndicatorData(fileName);
    }

    public static List<Double> getEmaData(String fileName) {
        return getIndicatorData(fileName);
    }

    public static List<Double> getBBData(String fileName) {
        return getIndicatorData(fileName);
    }

    private static List<Double> getIndicatorData(String filePath) {
        try {
            String smaValues = readFile(filePath);
            return Arrays.stream(smaValues.split("\n"))
                    .filter(s -> !s.isBlank())
                    .map(Double::parseDouble)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String readFile(String filename) throws IOException {
        Path path = Paths.get(filename);
        log.info("reading file from path : {}", path.toAbsolutePath());
        try (Stream<String> lines = Files.lines(path)) {
            return lines.collect(Collectors.joining("\n"));
        }
    }

    public static boolean contains(Candle candle, double value) {
        return candle.high() > value && candle.low() < value;
    }
}
