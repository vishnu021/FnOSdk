package com.vish.fno.util;

import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import static com.vish.fno.util.Constants.DATE_TIME_FORMAT;
import static org.junit.jupiter.api.Assertions.*;

class TimeUtilsTest {

    @Test
    public void test_get_index_of_time_before_market_opening() {
        //Arrange
        Calendar calendar = Calendar.getInstance();
        calendar.set(2023, Calendar.DECEMBER, 28, 8, 45, 0);
        Date timestamp = calendar.getTime();
        //Act
        int index = TimeUtils.getIndexOfTimeStamp(timestamp);
        //Assert
        assertEquals(index, -1);
    }

    @Test
    public void test_get_index_of_time_at_opening_time() {
        //Arrange
        Calendar calendar = Calendar.getInstance();
        calendar.set(2023, Calendar.DECEMBER, 28, 9, 15, 0);
        Date timestamp = calendar.getTime();
        //Act
        int index = TimeUtils.getIndexOfTimeStamp(timestamp);
        //Assert
        assertEquals(index, 0);
    }

    @Test
    public void test_get_index_of_time_at_close_time() {
        //Arrange
        Calendar calendar = Calendar.getInstance();
        calendar.set(2023, Calendar.DECEMBER, 28, 15, 30, 0);
        Date timestamp = calendar.getTime();
        //Act
        int index = TimeUtils.getIndexOfTimeStamp(timestamp);
        //Assert
        assertEquals(index, 375);
    }

    @Test
    public void test_get_index_of_time_after_market_hour() {
        //Arrange
        Calendar calendar = Calendar.getInstance();
        calendar.set(2023, Calendar.DECEMBER, 28, 15, 31, 0);
        Date timestamp = calendar.getTime();
        //Act
        int index = TimeUtils.getIndexOfTimeStamp(timestamp);
        //Assert
        assertEquals(index, -1);
    }

    @Test
    public void test_append_opening_time_to_date() {
        //Arrange
        SimpleDateFormat formatter = new SimpleDateFormat(DATE_TIME_FORMAT, Locale.ENGLISH);
        Calendar calendar = Calendar.getInstance();
        calendar.set(2023, Calendar.DECEMBER, 28, 0, 0, 0);
        Date date = calendar.getTime();
        //Act
        Date actualDate = TimeUtils.appendOpeningTimeToDate(date);
        //Assert
        assertEquals("2023-12-28 09:15", formatter.format(actualDate));
    }

    @Test
    public void test_append_opening_time_to_date_for_null_date() {
        assertThrows(NullPointerException.class, () -> {
            TimeUtils.appendOpeningTimeToDate(null);
        });
    }

    @Test
    public void test_append_closing_time_to_date() {
        //Arrange
        SimpleDateFormat formatter = new SimpleDateFormat(DATE_TIME_FORMAT, Locale.ENGLISH);
        Calendar calendar = Calendar.getInstance();
        calendar.set(2023, Calendar.DECEMBER, 28, 0, 0, 0);
        Date date = calendar.getTime();
        //Act
        Date actualDate = TimeUtils.appendClosingTimeToDate(date);
        //Assert
        assertEquals("2023-12-28 15:30", formatter.format(actualDate));
    }

    @Test
    public void test_append_closing_time_to_date_for_null_date() {
        assertThrows(NullPointerException.class, () -> {
            TimeUtils.appendClosingTimeToDate(null);
        });
    }

    @Test
    public void test_get_index_of_time() {
        //Arrange
        SimpleDateFormat formatter = new SimpleDateFormat(DATE_TIME_FORMAT, Locale.ENGLISH);
        Calendar calendar = Calendar.getInstance();
        calendar.set(2023, Calendar.DECEMBER, 28, 0, 0, 0);
        Date date = calendar.getTime();
        //Act
        Date actualDate = TimeUtils.appendClosingTimeToDate(date);
        //Assert
        assertEquals("2023-12-28 15:30", formatter.format(actualDate));
    }
}