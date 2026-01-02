package com.vish.fno.reader.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vish.fno.reader.util.InstrumentFileUtils;
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

import static com.vish.fno.util.FnoConstants.NIFTY_50;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Slf4j
class OptionPriceUtilsTest {

    private static final String INSTRUMENT_CACHE_FILE = "/src/test/java/resources/instrument_cache/instruments_2025-12-31.json";

    @Mock
    private KiteService kiteService;
    private InstrumentCache instrumentCache;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        List<Instrument>  instruments = mockInstrumentCache();
        List<String> nifty100Symbols = List.of("NIFTY", "NIFTY 50", "BANKNIFTY", "NIFTY BANK", "NIFTYNXT50", "FINNIFTY", "NIFTY FIN SERVICE", "BANKEX", "SENSEX", "SENSEX50");
        instrumentCache = new InstrumentCache(nifty100Symbols, kiteService);
        when(kiteService.getAllInstruments()).thenReturn(instruments);
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
            String indexSymbol = NIFTY_50;
            // Act
            String optionSymbol = OptionPriceUtils.getNextExpiryFutureSymbol(indexSymbol,  instrumentCache.getInstruments()).get();
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
            String indexSymbol = "SENSEX";
            // Act
            String optionSymbol = OptionPriceUtils.getNextExpiryFutureSymbol(indexSymbol,  instrumentCache.getInstruments()).get();
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
            String indexSymbol = "BANKEX";
            // Act
            String optionSymbol = OptionPriceUtils.getNextExpiryFutureSymbol(indexSymbol,  instrumentCache.getInstruments()).get();
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
            String indexSymbol = "SENSEX";
            // Act
            String optionSymbols = OptionPriceUtils.getITMStock(indexSymbol, 85352.0, false, instrumentCache.getInstruments());
            // Assert
            assertEquals(optionSymbols, "SENSEX2610185400PE");
        }
    }

//    @Test
//    void testGetPutOptionSymbolsForIndexAroundPrice() {
//
//        try(MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
//            //Arrange
//            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
//            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
//            // Arrange
//            String indexSymbol = NIFTY_50;
//
//            // Act
//            List<String> optionSymbols = OptionPriceUtils.getPutOptionSymbolsAroundPrice(indexSymbol, 23400d, instrumentCache.getInstruments());
//
//            // Assert
//            log.info("optionSymbols : {}", optionSymbols);
//        }
//        verify(kiteService, never()).sellOrder(any(), anyDouble(), anyInt(), any(), anyBoolean());
//    }
//
//    @Test
//    void testGetCallOptionSymbolsForIndexAroundPrice() {
//
//        try(MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
//            //Arrange
//            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
//            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
//            // Arrange
//            String indexSymbol = NIFTY_50;
//
//            // Act
//            List<String> optionSymbols = OptionPriceUtils.getCallOptionSymbolsAroundPrice(indexSymbol, 23400d, instrumentCache.getInstruments());
//
//            // Assert
//            log.info("optionSymbols : {}", optionSymbols);
//        }
////        verify(kiteService, never()).sellOrder(any(), anyDouble(), anyInt(), any(), anyBoolean());
//    }
}
