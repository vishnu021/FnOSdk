package com.vish.fno.reader.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerodhatech.models.Instrument;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InstrumentFileUtils {

    private final static ObjectMapper mapper = new ObjectMapper();
    private final static String directory = "instrument_cache";
    private final static String DATE_FORMAT = "yyyy-MM-dd";
    private final static String filePath = Paths.get(".").normalize().toAbsolutePath() + "\\" + directory + "\\";

    public static void saveInstrumentCache(List<Instrument> instruments) {
        try {
            final String instrumentFilePath = filePath + getInstrumentFileName(0);
            mapper.writeValue(new File(instrumentFilePath), instruments);
        } catch (IOException e) {
            log.error("Exception occurred while saving Instrument cache", e);
        }
    }
    public static void saveFilteredInstrumentCache(Object instruments) {
        try {
            final String instrumentFilePath = filePath + "filtered_" + getInstrumentFileName(0);
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(instrumentFilePath), instruments);
        } catch (IOException e) {
            log.error("Failed to save instrument cache.", e);
        }
    }
    private static String getInstrumentFileName(int days) {
        return "instruments_" + getFormattedDate(getNDaysBefore(days)) + ".json";
    }

    private static String getFormattedDate(Date date) {
        return new SimpleDateFormat(DATE_FORMAT, Locale.ENGLISH).format(date);
    }

    public static List<Instrument> loadInstrumentCache(int days) {
        List<Instrument> instruments = null;
        String instrumentFileName = getInstrumentFileName(days);
        try {
            instruments = mapper.readValue(new File(getInstrumentFileName(days)),
                    mapper.getTypeFactory().constructCollectionType(List.class, Instrument.class));
            log.info("Loaded instrument cache from file " + instrumentFileName);
        } catch (IOException e) {
            log.error("Instrument file not yet created");
        }
        return instruments;
    }

    public static Date getNDaysBefore(long n) {
        return new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(n));
    }
}
