package com.vish.fno.reader.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vish.fno.reader.util.InstrumentFileUtils;
import com.vish.fno.reader.util.OptionPriceUtils;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.models.Instrument;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static com.vish.fno.util.FnoConstants.NIFTY_50;
import static com.vish.fno.util.FnoConstants.NIFTY_BANK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Slf4j
class OptionPriceUtilsTest {

    private static final String INSTRUMENT_CACHE_FILE = "/src/test/java/resources/instrument_cache/instruments_2025-12-31.json";

    private static final List<String> NIFTY_100_SYMBOLS = List.of("NIFTY", "NIFTY 50", "BANKNIFTY", "NIFTY BANK", "NIFTYNXT50", "FINNIFTY", "NIFTY FIN SERVICE", "BANKEX", "SENSEX", "SENSEX50");

    @Mock
    private KiteSession session;
    @Mock
    private KiteConnect mockKiteSdk;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @SuppressWarnings("unchecked")
    private InstrumentCache createInstrumentCache() {
        List<Instrument> instruments = mockInstrumentCache();
        when(session.getKiteSdk()).thenReturn(mockKiteSdk);
        when(session.executeWithLock(any(Supplier.class), anyString()))
                .thenAnswer(invocation -> instruments);
        return new InstrumentCache(NIFTY_100_SYMBOLS, session);
    }

    @SneakyThrows
    private List<Instrument> mockInstrumentCache() {
        final File instrumentCacheFile = new File(System.getProperty("user.dir") + INSTRUMENT_CACHE_FILE);
        return mapper.readValue(instrumentCacheFile,
                mapper.getTypeFactory().constructCollectionType(List.class, Instrument.class));
    }

    @Test
    void testGetNextExpiryFutureSymbol() {
        try(MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            //Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();
            String indexSymbol = NIFTY_50;
            // Act
            String optionSymbol = OptionPriceUtils.getNextExpiryFutureSymbol(indexSymbol, instrumentCache.getInstruments()).get();
            // Assert
            assertEquals(optionSymbol, "NIFTY26JANFUT");
        }
    }

    @Test
    void testGetNextExpiryFutureSymbolSensex() {
        try(MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            //Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();
            String indexSymbol = "SENSEX";
            // Act
            String optionSymbol = OptionPriceUtils.getNextExpiryFutureSymbol(indexSymbol, instrumentCache.getInstruments()).get();
            // Assert
            assertEquals(optionSymbol, "SENSEX26JANFUT");
        }
    }

    @Test
    void testGetNextExpiryFutureSymbolBankex() {
        try(MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            //Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();
            String indexSymbol = "BANKEX";
            // Act
            String optionSymbol = OptionPriceUtils.getNextExpiryFutureSymbol(indexSymbol, instrumentCache.getInstruments()).get();
            // Assert
            assertEquals(optionSymbol, "BANKEX26JANFUT");
        }
    }

    @Test
    void testGetCallITMStockNifty() {
        try(MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            //Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();
            String indexSymbol = NIFTY_50;
            // Act
            String optionSymbols = OptionPriceUtils.getITMStock(indexSymbol, 24952.0, true, instrumentCache.getInstruments());
            // Assert
            assertEquals(optionSymbols, "NIFTY2610624950CE");
        }
    }

    @Test
    void testGetPutITMStockNifty() {
        try(MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            //Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();
            String indexSymbol = NIFTY_50;
            // Act
            String optionSymbols = OptionPriceUtils.getITMStock(indexSymbol, 24952.0, false, instrumentCache.getInstruments());
            // Assert
            assertEquals(optionSymbols, "NIFTY2610625000PE");
        }
    }

    @Test
    void testGetCallITMStockSensex() {
        try(MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            //Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();
            String indexSymbol = "SENSEX50";
            // Act
            String optionSymbols = OptionPriceUtils.getITMStock(indexSymbol, 85352.0, true, instrumentCache.getInstruments());
            // Assert
            assertEquals(optionSymbols, "SENSEX5026JAN28350CE");
        }
    }

    @Test
    void testGetPutITMStockSensex() {
        try(MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            //Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();
            String indexSymbol = "SENSEX";
            // Act
            String optionSymbols = OptionPriceUtils.getITMStock(indexSymbol, 85352.0, false, instrumentCache.getInstruments());
            // Assert
            assertEquals(optionSymbols, "SENSEX2610185400PE");
        }
    }

    /**
     * Test getAllOptionSymbols() returns a non-empty list for NIFTY 50.
     */
    @Test
    void testGetAllOptionSymbols_nifty() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // Act
            List<String> optionSymbols = OptionPriceUtils.getAllOptionSymbols(NIFTY_50, instrumentCache.getInstruments());

            // Assert
            assertNotNull(optionSymbols, "Option symbols list should not be null");
            assertFalse(optionSymbols.isEmpty(), "Option symbols list should not be empty for NIFTY 50");

            // Verify the list contains both CE and PE symbols
            boolean hasCE = optionSymbols.stream().anyMatch(s -> s.contains("CE"));
            boolean hasPE = optionSymbols.stream().anyMatch(s -> s.contains("PE"));
            assertTrue(hasCE, "Should contain CE (call) option symbols");
            assertTrue(hasPE, "Should contain PE (put) option symbols");
            log.info("Total NIFTY 50 option symbols: {}", optionSymbols.size());
        }
    }

    /**
     * Test getAllOptionSymbols() returns an empty list for an unknown index.
     */
    @Test
    void testGetAllOptionSymbols_unknownIndex() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // Act
            List<String> optionSymbols = OptionPriceUtils.getAllOptionSymbols("UNKNOWNINDEX", instrumentCache.getInstruments());

            // Assert
            assertNotNull(optionSymbols, "Option symbols list should not be null");
            assertTrue(optionSymbols.isEmpty(), "Option symbols list should be empty for an unknown index");
        }
    }

    /**
     * Test getITMStock() when price is exactly at a strike boundary (e.g., 25000.0).
     * For a call ITM: last strike below or at price = 25000 CE
     * For a put ITM: first strike above or at price = 25000 PE
     */
    @Test
    void testGetITMStock_atExactStrike() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // Act - price exactly at 25000
            String callITM = OptionPriceUtils.getITMStock(NIFTY_50, 25000.0, true, instrumentCache.getInstruments());
            String putITM = OptionPriceUtils.getITMStock(NIFTY_50, 25000.0, false, instrumentCache.getInstruments());

            // Assert
            assertNotNull(callITM, "ITM call should not be null at exact strike");
            assertNotNull(putITM, "ITM put should not be null at exact strike");
            // ITM call selects last strike <= price, so at 25000 the last strike not exceeding is 25000
            assertTrue(callITM.contains("CE"), "ITM call symbol should contain CE");
            assertTrue(putITM.contains("PE"), "ITM put symbol should contain PE");
            // At exactly 25000, call ITM = 25000CE (last not exceeding), put ITM = 25050PE (first above)
            assertTrue(callITM.contains("25000"), "ITM call at exact 25000 should be the 25000 strike");
            assertTrue(putITM.contains("25050"), "ITM put at exact 25000 should be the next strike above (25050)");
            log.info("At exact strike 25000: call ITM={}, put ITM={}", callITM, putITM);
        }
    }

    /**
     * Test getOTMStock() for call returns the first strike above price.
     */
    @Test
    void testGetOTMStock_call() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // Act - OTM call: first strike above price
            String otmCall = OptionPriceUtils.getOTMStock(NIFTY_50, 24952.0, true, instrumentCache.getInstruments());

            // Assert
            assertNotNull(otmCall, "OTM call should not be null");
            assertTrue(otmCall.contains("CE"), "OTM call symbol should contain CE");
            // OTM call = first strike above 24952 = 25000CE
            assertTrue(otmCall.contains("25000"), "OTM call at 24952 should be 25000 strike");
            log.info("OTM call at 24952: {}", otmCall);
        }
    }

    /**
     * Test getOTMStock() for put returns the last strike below price.
     */
    @Test
    void testGetOTMStock_put() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // Act - OTM put: last strike below price
            String otmPut = OptionPriceUtils.getOTMStock(NIFTY_50, 24952.0, false, instrumentCache.getInstruments());

            // Assert
            assertNotNull(otmPut, "OTM put should not be null");
            assertTrue(otmPut.contains("PE"), "OTM put symbol should contain PE");
            // OTM put = last strike below 24952 = 24950PE
            assertTrue(otmPut.contains("24950"), "OTM put at 24952 should be 24950 strike");
            log.info("OTM put at 24952: {}", otmPut);
        }
    }

    /**
     * Test getNextExpiryFutureSymbol() returns empty Optional for an unknown symbol.
     */
    @Test
    void testGetNextExpiryFutureSymbol_unknownSymbol() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // Act
            Optional<String> futureSymbol = OptionPriceUtils.getNextExpiryFutureSymbol("UNKNOWNSYMBOL", instrumentCache.getInstruments());

            // Assert
            assertTrue(futureSymbol.isEmpty(), "Future symbol should be empty for an unknown symbol");
        }
    }

    /**
     * Test getITMStock() works correctly for NIFTY BANK index.
     */
    @Test
    void testGetITMStock_bankNifty() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // Act - NIFTY BANK at price 52150
            String callITM = OptionPriceUtils.getITMStock(NIFTY_BANK, 52150.0, true, instrumentCache.getInstruments());
            String putITM = OptionPriceUtils.getITMStock(NIFTY_BANK, 52150.0, false, instrumentCache.getInstruments());

            // Assert
            assertNotNull(callITM, "ITM call should not be null for NIFTY BANK");
            assertNotNull(putITM, "ITM put should not be null for NIFTY BANK");
            assertFalse(callITM.isEmpty(), "ITM call should not be empty for NIFTY BANK");
            assertFalse(putITM.isEmpty(), "ITM put should not be empty for NIFTY BANK");
            assertTrue(callITM.contains("CE"), "ITM call should contain CE");
            assertTrue(putITM.contains("PE"), "ITM put should contain PE");
            assertTrue(callITM.startsWith("BANKNIFTY"), "ITM call should start with BANKNIFTY");
            assertTrue(putITM.startsWith("BANKNIFTY"), "ITM put should start with BANKNIFTY");
            log.info("NIFTY BANK ITM at 52150: call={}, put={}", callITM, putITM);
        }
    }
}
