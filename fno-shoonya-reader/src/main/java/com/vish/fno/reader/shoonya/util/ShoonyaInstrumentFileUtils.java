package com.vish.fno.reader.shoonya.util;

import com.vish.fno.reader.shoonya.model.ShoonyaInstrument;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ShoonyaInstrumentFileUtils {

    private static final String INSTRUMENT_BASE_URL = "https://api.shoonya.com/";
    private static final int NFO_COLUMN_COUNT = 10;
    private static final int NSE_COLUMN_COUNT = 7;

    public static List<ShoonyaInstrument> downloadAndParseInstruments(ShoonyaHttpClient httpClient,
                                                                      List<String> exchanges) {
        List<ShoonyaInstrument> allInstruments = new ArrayList<>();
        for (String exchange : exchanges) {
            try {
                String url = INSTRUMENT_BASE_URL + exchange + "_symbols.txt.zip";
                byte[] zipData = httpClient.download(url);
                List<ShoonyaInstrument> instruments = parseZippedCsv(zipData, exchange);
                allInstruments.addAll(instruments);
                log.info("Loaded {} instruments from {} contract master", instruments.size(), exchange);
            } catch (IOException e) {
                log.error("Failed to download {} instrument data", exchange, e);
            }
        }
        return allInstruments;
    }

    static List<ShoonyaInstrument> parseZippedCsv(byte[] zipData, String exchange) throws IOException {
        List<ShoonyaInstrument> instruments = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData));
             InputStreamReader isr = new InputStreamReader(zis, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {
            ZipEntry entry = zis.getNextEntry();
            if (entry == null) {
                log.warn("Empty zip file for exchange {}", exchange);
                return instruments;
            }
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.isBlank()) {
                    continue;
                }
                try {
                    ShoonyaInstrument instrument = parseCsvLine(line, exchange);
                    if (instrument != null) {
                        instruments.add(instrument);
                    }
                } catch (NumberFormatException e) {
                    if (lineNum <= 2) {
                        log.debug("Skipping header/malformed line {} in {} CSV: {}", lineNum, exchange, line);
                    }
                }
            }
        }

        return instruments;
    }

    private static ShoonyaInstrument parseCsvLine(String line, String exchange) {
        String[] parts = line.split(",", -1);

        if (parts.length >= NFO_COLUMN_COUNT && isDerivativeExchange(exchange)) {
            return new ShoonyaInstrument(
                parts[0].trim(),
                Long.parseLong(parts[1].trim()),
                Integer.parseInt(parts[2].trim()),
                parts[3].trim(),
                parts[4].trim(),
                parts[5].isBlank() ? null : parts[5].trim(),
                parts[6].trim(),
                parts[7].trim(),
                Double.parseDouble(parts[8].trim()),
                Double.parseDouble(parts[9].trim())
            );
        } else if (parts.length >= NSE_COLUMN_COUNT) {
            return new ShoonyaInstrument(
                parts[0].trim(),
                Long.parseLong(parts[1].trim()),
                Integer.parseInt(parts[2].trim()),
                parts[3].trim(),
                parts[4].trim(),
                null,
                parts[5].trim(),
                null,
                0.0,
                Double.parseDouble(parts[6].trim())
            );
        }

        return null;
    }

    private static boolean isDerivativeExchange(String exchange) {
        return "NFO".equals(exchange) || "BFO".equals(exchange);
    }
}
