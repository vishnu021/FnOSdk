package com.vish.fno.util.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vish.fno.model.Ticker;
import com.vish.fno.util.JsonUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CompressionUtils {

    // VT-safe ObjectMapper: uses shared bounded pool instead of ThreadLocal BufferRecycler
    private static final ObjectMapper objectMapper = JsonUtils.createObjectMapper();

    // Method to compress a List of Tickers
    public static byte[] compressTickers(List<Ticker> tickers) throws IOException {
        // Serialize the list of Tickers to JSON
        String jsonTickers = objectMapper.writeValueAsString(tickers);

        // Compress the JSON string
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
            gzipOutputStream.write(jsonTickers.getBytes(StandardCharsets.UTF_8));
        }
        return byteArrayOutputStream.toByteArray(); // Return compressed data
    }

    // Method to decompress and deserialize a List of Tickers
    public static List<Ticker> decompressTickers(byte[] compressedData) throws IOException {
        // Decompress the data
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(compressedData);
        StringBuilder outStr = new StringBuilder();
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream)) {
            byte[] buffer = new byte[256];
            int n;
            while ((n = gzipInputStream.read(buffer)) >= 0) {
                outStr.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
            }
        }
        String jsonTickers = outStr.toString();

        // Deserialize the JSON string back to List<Ticker>
        return objectMapper.readValue(jsonTickers,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Ticker.class));
    }
}
