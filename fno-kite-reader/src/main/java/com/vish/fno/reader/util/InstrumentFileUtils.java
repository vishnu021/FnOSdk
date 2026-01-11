package com.vish.fno.reader.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerodhatech.models.Instrument;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InstrumentFileUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DIRECTORY = "instrument_cache";
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    // Thread-safe DateTimeFormatter (immutable and thread-safe)
    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern(DATE_FORMAT, Locale.ENGLISH);

    // Platform-independent file path
    private static final Path FILE_PATH =
        Paths.get(".").normalize().toAbsolutePath().resolve(DIRECTORY);

    public static void saveInstrumentCache(List<Instrument> instruments) {
        try {
            ensureDirectoryExists();
            final String instrumentFilePath = FILE_PATH.resolve(getInstrumentFileName(0)).toString();
            MAPPER.writeValue(new File(instrumentFilePath), instruments);
        } catch (IOException e) {
            log.error("Exception occurred while saving Instrument cache", e);
        }
    }

    public static void saveFilteredInstrumentCache(Object instruments) {
        try {
            ensureDirectoryExists();
            final String instrumentFilePath = FILE_PATH.resolve("filtered_" + getInstrumentFileName(0)).toString();
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(instrumentFilePath), instruments);
        } catch (IOException e) {
            log.error("Failed to save instrument cache.", e);
        }
    }

    private static void ensureDirectoryExists() throws IOException {
        if (!Files.exists(FILE_PATH)) {
            Files.createDirectories(FILE_PATH);
            log.info("Created instrument cache directory: {}", FILE_PATH);
        }
    }

    private static String getInstrumentFileName(int days) {
        return "instruments_" + getFormattedDate(getNDaysBefore(days)) + ".json";
    }

    /**
     * Formats a date to string (thread-safe).
     *
     * @param date the date to format
     * @return formatted date string
     */
    private static String getFormattedDate(Date date) {
        return DATE_FORMATTER.format(date.toInstant().atZone(ZoneId.systemDefault()));
    }

    public static List<Instrument> loadInstrumentCache(int days) {
        List<Instrument> instruments = null;
        String instrumentFileName = getInstrumentFileName(days);
        try {
            instruments = MAPPER.readValue(
                new File(FILE_PATH.resolve(instrumentFileName).toString()),
                MAPPER.getTypeFactory().constructCollectionType(List.class, Instrument.class)
            );
            log.info("Loaded instrument cache from file {}", instrumentFileName);
        } catch (IOException e) {
            log.error("Instrument file not yet created");
        }
        return instruments;
    }

    public static Date getNDaysBefore(long n) {
        return new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(n));
    }
}
