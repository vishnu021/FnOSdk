package com.vish.fno.reader.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerodhatech.models.Instrument;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
class InstrumentFileUtilsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    private Path originalFilePath;

    @BeforeEach
    void setUp() throws Exception {
        originalFilePath = getFilePathFieldValue();
        setFilePathField(tempDir);
    }

    @AfterEach
    void tearDown() throws Exception {
        setFilePathField(originalFilePath);
    }

    // ---- loadInstrumentCache tests ----

    @Test
    void loadInstrumentCacheReturnsEmptyListWhenFileNotFound() {
        // Act - file does not exist in the temp directory
        List<Instrument> result = InstrumentFileUtils.loadInstrumentCache(999);

        // Assert
        assertNotNull(result, "Result should not be null even when file is missing");
        assertTrue(result.isEmpty(), "Should return empty list when cache file does not exist");
    }

    @Test
    void loadInstrumentCacheReturnsEmptyListForCurrentDayWhenNoFileExists() {
        // Act - no file has been saved yet for today (days=0)
        List<Instrument> result = InstrumentFileUtils.loadInstrumentCache(0);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertTrue(result.isEmpty(), "Should return empty list when no cache file exists for today");
    }

    // ---- saveInstrumentCache tests ----

    @Test
    void saveInstrumentCacheCreatesFileSuccessfully() {
        // Arrange
        List<Instrument> instruments = createSampleInstruments();

        // Act
        InstrumentFileUtils.saveInstrumentCache(instruments);

        // Assert - verify a file was created in the temp directory
        File[] files = tempDir.toFile().listFiles((dir, name) -> name.startsWith("instruments_") && name.endsWith(".json"));
        assertNotNull(files, "Files array should not be null");
        assertEquals(1, files.length, "Exactly one instrument file should be created");
        assertTrue(files[0].length() > 0, "Instrument file should not be empty");
        log.info("Created instrument file: {} ({} bytes)", files[0].getName(), files[0].length());
    }

    @Test
    void saveInstrumentCacheWithEmptyList() {
        // Act
        assertDoesNotThrow(() -> InstrumentFileUtils.saveInstrumentCache(List.of()),
                "Saving an empty list should not throw an exception");

        // Assert - file should still be created (with empty JSON array)
        File[] files = tempDir.toFile().listFiles((dir, name) -> name.startsWith("instruments_") && name.endsWith(".json"));
        assertNotNull(files, "Files array should not be null");
        assertEquals(1, files.length, "File should be created even for empty list");
    }

    @Test
    void saveInstrumentCacheCreatesDirectoryIfNotExists() throws Exception {
        // Arrange - point FILE_PATH to a non-existent subdirectory
        Path nestedDir = tempDir.resolve("nested").resolve("cache");
        setFilePathField(nestedDir);
        List<Instrument> instruments = createSampleInstruments();

        // Act
        InstrumentFileUtils.saveInstrumentCache(instruments);

        // Assert
        assertTrue(Files.exists(nestedDir), "Nested directory should be created");
        File[] files = nestedDir.toFile().listFiles((dir, name) -> name.startsWith("instruments_") && name.endsWith(".json"));
        assertNotNull(files, "Files array should not be null");
        assertEquals(1, files.length, "Instrument file should be created in nested directory");
    }

    // ---- saveFilteredInstrumentCache tests ----

    @Test
    void saveFilteredInstrumentCacheCreatesFileSuccessfully() {
        // Arrange
        List<Instrument> instruments = createSampleInstruments();

        // Act
        InstrumentFileUtils.saveFilteredInstrumentCache(instruments);

        // Assert
        File[] files = tempDir.toFile().listFiles((dir, name) -> name.startsWith("filtered_instruments_") && name.endsWith(".json"));
        assertNotNull(files, "Files array should not be null");
        assertEquals(1, files.length, "Exactly one filtered instrument file should be created");
        assertTrue(files[0].length() > 0, "Filtered instrument file should not be empty");
        log.info("Created filtered instrument file: {} ({} bytes)", files[0].getName(), files[0].length());
    }

    @Test
    void saveFilteredInstrumentCacheWithMapObject() {
        // Arrange - saveFilteredInstrumentCache accepts Object, not just List
        Map<String, String> filteredData = Map.of("NIFTY2610624950CE", "NIFTY", "BANKNIFTY26106CE", "BANKNIFTY");

        // Act
        assertDoesNotThrow(() -> InstrumentFileUtils.saveFilteredInstrumentCache(filteredData),
                "Saving a map object should not throw an exception");

        // Assert
        File[] files = tempDir.toFile().listFiles((dir, name) -> name.startsWith("filtered_instruments_") && name.endsWith(".json"));
        assertNotNull(files, "Files array should not be null");
        assertEquals(1, files.length, "Filtered file should be created for map input");
    }

    @Test
    void saveFilteredInstrumentCacheWritesPrettyPrintedJson() throws IOException {
        // Arrange
        List<Instrument> instruments = createSampleInstruments();

        // Act
        InstrumentFileUtils.saveFilteredInstrumentCache(instruments);

        // Assert - pretty printed JSON should contain newlines and indentation
        File[] files = tempDir.toFile().listFiles((dir, name) -> name.startsWith("filtered_instruments_"));
        assertNotNull(files, "Files array should not be null");
        assertEquals(1, files.length, "One filtered file should exist");
        String content = Files.readString(files[0].toPath());
        assertTrue(content.contains("\n"), "Pretty-printed JSON should contain newlines");
        assertTrue(content.contains("  "), "Pretty-printed JSON should contain indentation");
    }

    // ---- save + load roundtrip tests ----

    @Test
    void saveAndLoadRoundtripPreservesInstrumentData() {
        // Arrange
        List<Instrument> originalInstruments = createSampleInstruments();

        // Act
        InstrumentFileUtils.saveInstrumentCache(originalInstruments);
        List<Instrument> loadedInstruments = InstrumentFileUtils.loadInstrumentCache(0);

        // Assert
        assertNotNull(loadedInstruments, "Loaded instruments should not be null");
        assertEquals(originalInstruments.size(), loadedInstruments.size(),
                "Loaded instruments count should match saved count");

        // Verify field values are preserved
        Instrument original = originalInstruments.get(0);
        Instrument loaded = loadedInstruments.get(0);
        assertEquals(original.getInstrument_token(), loaded.getInstrument_token(),
                "Instrument token should be preserved after roundtrip");
        assertEquals(original.getTradingsymbol(), loaded.getTradingsymbol(),
                "Trading symbol should be preserved after roundtrip");
        assertEquals(original.getName(), loaded.getName(),
                "Name should be preserved after roundtrip");
        assertEquals(original.getExchange(), loaded.getExchange(),
                "Exchange should be preserved after roundtrip");
        assertEquals(original.getLot_size(), loaded.getLot_size(),
                "Lot size should be preserved after roundtrip");
        assertEquals(original.getInstrument_type(), loaded.getInstrument_type(),
                "Instrument type should be preserved after roundtrip");
    }

    @Test
    void saveAndLoadRoundtripWithMultipleInstruments() {
        // Arrange
        List<Instrument> instruments = new ArrayList<>();
        instruments.add(createInstrument(11111L, "NIFTY2610624950CE", "NIFTY", "NFO", "CE", 75));
        instruments.add(createInstrument(22222L, "NIFTY2610625000PE", "NIFTY", "NFO", "PE", 75));
        instruments.add(createInstrument(33333L, "BANKNIFTY26106CE", "BANKNIFTY", "NFO", "CE", 30));
        instruments.add(createInstrument(44444L, "NIFTYFUT", "NIFTY", "NFO", "FUT", 75));

        // Act
        InstrumentFileUtils.saveInstrumentCache(instruments);
        List<Instrument> loaded = InstrumentFileUtils.loadInstrumentCache(0);

        // Assert
        assertEquals(4, loaded.size(), "All four instruments should be loaded");
        assertEquals("NIFTY2610624950CE", loaded.get(0).getTradingsymbol());
        assertEquals("BANKNIFTY26106CE", loaded.get(2).getTradingsymbol());
        assertEquals("FUT", loaded.get(3).getInstrument_type());
    }

    @Test
    void saveAndLoadRoundtripWithEmptyList() {
        // Act
        InstrumentFileUtils.saveInstrumentCache(List.of());
        List<Instrument> loaded = InstrumentFileUtils.loadInstrumentCache(0);

        // Assert
        assertNotNull(loaded, "Loaded instruments should not be null");
        assertTrue(loaded.isEmpty(), "Should load empty list from empty saved list");
    }

    // ---- filename and days parameter tests ----

    @Test
    void loadInstrumentCacheWithDifferentDaysDoesNotFindFile() {
        // Arrange - save for today (days=0)
        InstrumentFileUtils.saveInstrumentCache(createSampleInstruments());

        // Act - try to load for yesterday (days=1)
        List<Instrument> result = InstrumentFileUtils.loadInstrumentCache(1);

        // Assert - file for yesterday does not exist
        assertTrue(result.isEmpty(),
                "Loading with different days parameter should not find today's file");
    }

    @Test
    void saveCreatesFileWithTodaysDateInName() {
        // Act
        InstrumentFileUtils.saveInstrumentCache(createSampleInstruments());

        // Assert - verify the filename contains a date pattern
        File[] files = tempDir.toFile().listFiles((dir, name) -> name.startsWith("instruments_"));
        assertNotNull(files, "Files array should not be null");
        assertEquals(1, files.length, "One instrument file should exist");

        String fileName = files[0].getName();
        assertTrue(fileName.matches("instruments_\\d{4}-\\d{2}-\\d{2}\\.json"),
                "Filename should match pattern instruments_YYYY-MM-DD.json, but was: " + fileName);
    }

    // ---- both save methods create files with consistent naming ----

    @Test
    void saveAndSaveFilteredCreateDistinctFiles() {
        // Arrange
        List<Instrument> instruments = createSampleInstruments();

        // Act
        InstrumentFileUtils.saveInstrumentCache(instruments);
        InstrumentFileUtils.saveFilteredInstrumentCache(instruments);

        // Assert
        File[] allFiles = tempDir.toFile().listFiles((dir, name) -> name.endsWith(".json"));
        assertNotNull(allFiles, "Files array should not be null");
        assertEquals(2, allFiles.length, "Both regular and filtered files should be created");

        File[] regularFiles = tempDir.toFile().listFiles(
                (dir, name) -> name.startsWith("instruments_") && !name.startsWith("filtered_"));
        File[] filteredFiles = tempDir.toFile().listFiles(
                (dir, name) -> name.startsWith("filtered_instruments_"));

        assertNotNull(regularFiles, "Regular files array should not be null");
        assertNotNull(filteredFiles, "Filtered files array should not be null");
        assertEquals(1, regularFiles.length, "One regular file should exist");
        assertEquals(1, filteredFiles.length, "One filtered file should exist");
    }

    // ---- edge case: instrument with null/default fields ----

    @Test
    void saveAndLoadInstrumentWithMinimalFields() {
        // Arrange - instrument with only default values
        Instrument instrument = new Instrument();
        instrument.instrument_token = 12345L;
        instrument.tradingsymbol = "TESTSYMBOL";
        List<Instrument> instruments = List.of(instrument);

        // Act
        InstrumentFileUtils.saveInstrumentCache(instruments);
        List<Instrument> loaded = InstrumentFileUtils.loadInstrumentCache(0);

        // Assert
        assertFalse(loaded.isEmpty(), "Should load instrument with minimal fields");
        assertEquals(12345L, loaded.get(0).getInstrument_token());
        assertEquals("TESTSYMBOL", loaded.get(0).getTradingsymbol());
    }

    // ---- helper methods ----

    private List<Instrument> createSampleInstruments() {
        List<Instrument> instruments = new ArrayList<>();
        instruments.add(createInstrument(293244165L, "NIFTY2610624950CE", "NIFTY", "NFO", "CE", 75));
        instruments.add(createInstrument(298595077L, "BANKNIFTY26FEBFUT", "BANKNIFTY", "NFO", "FUT", 30));
        return instruments;
    }

    private Instrument createInstrument(long token, String tradingSymbol, String name,
                                        String exchange, String instrumentType, int lotSize) {
        Instrument instrument = new Instrument();
        instrument.instrument_token = token;
        instrument.exchange_token = token / 256;
        instrument.tradingsymbol = tradingSymbol;
        instrument.name = name;
        instrument.exchange = exchange;
        instrument.instrument_type = instrumentType;
        instrument.lot_size = lotSize;
        instrument.last_price = 0.0;
        instrument.tick_size = 0.05;
        instrument.segment = exchange + "-OPT";
        instrument.strike = "24950";
        instrument.expiry = new Date();
        return instrument;
    }

    /**
     * Retrieves the current value of the static final FILE_PATH field via reflection.
     */
    private Path getFilePathFieldValue() throws Exception {
        Field field = InstrumentFileUtils.class.getDeclaredField("FILE_PATH");
        field.setAccessible(true);
        return (Path) field.get(null);
    }

    /**
     * Sets the static final FILE_PATH field to a new value via reflection.
     * Uses Java's Unsafe to bypass the final modifier restriction in Java 17+.
     */
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private void setFilePathField(Path newPath) throws Exception {
        Field field = InstrumentFileUtils.class.getDeclaredField("FILE_PATH");
        field.setAccessible(true);

        // Use sun.misc.Unsafe to modify static final field in Java 17+
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);

        Object fieldBase = unsafe.staticFieldBase(field);
        long fieldOffset = unsafe.staticFieldOffset(field);
        unsafe.putObject(fieldBase, fieldOffset, newPath);
    }
}
