package com.vish.fno.reader.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vish.fno.reader.util.InstrumentFileUtils;
import com.zerodhatech.models.Instrument;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Slf4j
class InstrumentCacheTest {
    private static final String INSTRUMENT_CACHE_FILE = "/src/test/java/resources/instrument_cache/instruments_2024-07-04.json";

    @Mock private KiteService kiteService;

    private InstrumentCache underTest;
    private final ObjectMapper mapper = new ObjectMapper();


    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        List<String> nifty100Symbols = List.of("NIFTY 50", "NIFTY BANK", "HDFCBANK", "BANKNIFTY", "NIFTY");
        underTest = new InstrumentCache(nifty100Symbols, kiteService);
        List<Instrument> instruments = mockInstrumentCache();
        when(kiteService.getAllInstruments()).thenReturn(instruments);
    }

    @SneakyThrows
    private List<Instrument> mockInstrumentCache() {
        final File instrumentCacheFile = new File(System.getProperty("user.dir") + INSTRUMENT_CACHE_FILE);
        return mapper.readValue(instrumentCacheFile,
                    mapper.getTypeFactory().constructCollectionType(List.class, Instrument.class));
    }

    @Test
    void isExpiryDayForOptionForFutureDate() {
        try(MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            //Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            String optionSymbol = "NIFTY23DEC25000PE";

            Calendar calendar = Calendar.getInstance();
            calendar.set(2024, Calendar.DECEMBER, 28, 0, 0, 0);
            Date date = calendar.getTime();
            // Act
            boolean isExpiryDayForOption = underTest.isExpiryDayForOption(optionSymbol, date);
            // Assert
            assertFalse(isExpiryDayForOption);
        }
    }

    @Test
    void isExpiryDayForOptionOnExpiryDay() {
        try(MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            //Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            String optionSymbol = "NIFTY24JUL25000PE";

            Calendar calendar = Calendar.getInstance();
            calendar.set(2024, Calendar.JULY, 25, 0, 0, 0);
            Date date = calendar.getTime();
            // Act
            boolean isExpiryDayForOption = underTest.isExpiryDayForOption(optionSymbol, date);
            // Assert
            assertTrue(isExpiryDayForOption);
        }
    }

    @Test
    void isExpiryDayForOptionForDayBeforeExpiry() {
        try(MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            //Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            String optionSymbol = "NIFTY24JUL25000PE";

            Calendar calendar = Calendar.getInstance();
            calendar.set(2024, Calendar.JULY, 27, 0, 0, 0);

            Date date = calendar.getTime();
            // Act
            boolean isExpiryDayForOption = underTest.isExpiryDayForOption(optionSymbol, date);
            // Assert
            assertFalse(isExpiryDayForOption);
        }
    }
}