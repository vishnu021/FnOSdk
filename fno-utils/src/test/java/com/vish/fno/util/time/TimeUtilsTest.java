package com.vish.fno.util.time;

import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.vish.fno.util.FnoConstants.DATE_TIME_FORMAT;
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

    /**
     * Thread-safety test: Concurrent date formatting
     * Verifies that DateTimeFormatter-based methods are thread-safe
     */
    @Test
    public void testConcurrentDateFormatting() throws InterruptedException, ExecutionException {
        // Arrange
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, Calendar.JANUARY, 15, 10, 30, 45);
        Date testDate = calendar.getTime();

        int numThreads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<String>> futures = new ArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);

        // Act - multiple threads formatting the same date
        for (int i = 0; i < numThreads; i++) {
            Future<String> future = executor.submit(() -> {
                try {
                    startLatch.await();
                    return TimeUtils.getStringDate(testDate);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            });
            futures.add(future);
        }

        startLatch.countDown();

        // Collect results
        List<String> results = new ArrayList<>();
        for (Future<String> future : futures) {
            results.add(future.get());
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Assert - all results should be identical and correct
        String expectedDate = "2024-01-15";
        for (String result : results) {
            assertEquals(expectedDate, result);
        }
    }

    /**
     * Thread-safety test: Concurrent time parsing
     * Verifies parseCandlestickTimestamp is thread-safe
     */
    @Test
    public void testConcurrentTimeParsing() throws InterruptedException, ExecutionException {
        // Arrange
        String isoTimestamp = "2024-01-15T10:30:45+0530";
        int numThreads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<Long>> futures = new ArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);

        // Act - multiple threads parsing the same timestamp
        for (int i = 0; i < numThreads; i++) {
            Future<Long> future = executor.submit(() -> {
                try {
                    startLatch.await();
                    return TimeUtils.parseCandlestickTimestamp(isoTimestamp);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            });
            futures.add(future);
        }

        startLatch.countDown();

        // Collect results
        Set<Long> uniqueResults = new HashSet<>();
        for (Future<Long> future : futures) {
            Long result = future.get();
            assertNotNull(result);
            uniqueResults.add(result);
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Assert - all results should be identical (no race conditions)
        assertEquals(1, uniqueResults.size(), "Parsing should produce consistent results");
    }

    /**
     * Thread-safety test: Mixed date operations
     * Verifies multiple different date operations can run concurrently
     */
    @Test
    public void testConcurrentMixedDateOperations() throws InterruptedException, ExecutionException {
        // Arrange
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, Calendar.MARCH, 20, 14, 45, 30);
        Date testDate = calendar.getTime();

        int numThreads = 60;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<Boolean>> futures = new ArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);

        // Act - threads perform different operations
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            Future<Boolean> future = executor.submit(() -> {
                try {
                    startLatch.await();

                    // Mix of different operations
                    if (threadId % 6 == 0) {
                        String result = TimeUtils.getStringDate(testDate);
                        assertNotNull(result);
                    } else if (threadId % 6 == 1) {
                        Optional<String> result = TimeUtils.getStringDateTime(testDate);
                        assertTrue(result.isPresent());
                    } else if (threadId % 6 == 2) {
                        Optional<String> result = TimeUtils.getTime(testDate);
                        assertTrue(result.isPresent());
                    } else if (threadId % 6 == 3) {
                        String result = TimeUtils.getStringYear(testDate);
                        assertNotNull(result);
                    } else if (threadId % 6 == 4) {
                        long result = TimeUtils.parseCandlestickTimestamp("1710936330000");
                        assertTrue(result > 0);
                    } else {
                        String result = TimeUtils.formatDateTime(testDate.getTime());
                        assertNotNull(result);
                    }

                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                } catch (Exception e) {
                    // Any exception indicates thread-safety issue
                    return false;
                }
            });
            futures.add(future);
        }

        startLatch.countDown();

        // Collect results
        for (Future<Boolean> future : futures) {
            assertTrue(future.get(), "Thread encountered exception during execution");
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * Thread-safety test: Stress test with high concurrency
     * Verifies DateTimeFormatter handles extreme load
     */
    @Test
    public void testHighConcurrencyDateFormatting() throws InterruptedException, ExecutionException {
        // Arrange
        int numThreads = 100;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<Integer>> futures = new ArrayList<>();

        // Act - high-frequency date operations
        for (int t = 0; t < numThreads; t++) {
            Future<Integer> future = executor.submit(() -> {
                int successCount = 0;
                for (int i = 0; i < operationsPerThread; i++) {
                    try {
                        Calendar cal = Calendar.getInstance();
                        cal.set(2024, Calendar.JANUARY, i % 28 + 1, 10, 30, 0);
                        Date date = cal.getTime();

                        // Perform multiple operations
                        String strDate = TimeUtils.getStringDate(date);
                        Optional<String> strDateTime = TimeUtils.getStringDateTime(date);
                        Optional<String> time = TimeUtils.getTime(date);

                        // Verify results are present
                        if (strDate != null && strDateTime.isPresent() && time.isPresent()) {
                            successCount++;
                        }
                    } catch (Exception e) {
                        // Thread-safety issue detected
                        break;
                    }
                }
                return successCount;
            });
            futures.add(future);
        }

        // Collect results
        int totalSuccesses = 0;
        for (Future<Integer> future : futures) {
            totalSuccesses += future.get();
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Assert - all operations should succeed
        int expectedSuccesses = numThreads * operationsPerThread;
        assertEquals(expectedSuccesses, totalSuccesses,
            "Some operations failed due to thread-safety issues");
    }

    /**
     * Thread-safety test: Date parsing with multiple formats
     * Verifies parseDateTimeToEpoch is thread-safe
     */
    @Test
    public void testConcurrentDateTimeParsing() throws InterruptedException, ExecutionException {
        // Arrange
        String[] testDates = {
            "2024-01-15 10:30:45",
            "2024-03-20",
            "2024-12-31 23:59:59"
        };

        int numThreads = 30;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<Long>> futures = new ArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);

        // Act - threads parse different date formats
        for (int i = 0; i < numThreads; i++) {
            final String dateStr = testDates[i % testDates.length];
            Future<Long> future = executor.submit(() -> {
                try {
                    startLatch.await();
                    return TimeUtils.parseDateTimeToEpoch(dateStr);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            });
            futures.add(future);
        }

        startLatch.countDown();

        // Collect results
        for (Future<Long> future : futures) {
            Long result = future.get();
            assertNotNull(result);
            assertTrue(result > 0, "Parsed timestamp should be positive");
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * Thread-safety test: Concurrent access to timeArray
     * Verifies the static timeArray list is safely accessible
     */
    @Test
    public void testConcurrentTimeArrayAccess() throws InterruptedException, ExecutionException {
        // Arrange
        int numThreads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<Boolean>> futures = new ArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);

        // Act - threads access timeArray simultaneously
        for (int i = 0; i < numThreads; i++) {
            Future<Boolean> future = executor.submit(() -> {
                try {
                    startLatch.await();

                    // Access various indices
                    String time1 = TimeUtils.getTimeByIndex(0);
                    String time2 = TimeUtils.getTimeByIndex(100);
                    String time3 = TimeUtils.getTimeByIndex(375);

                    // Verify expected values
                    return "09:15".equals(time1)
                        && "10:55".equals(time2)
                        && "15:30".equals(time3);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            });
            futures.add(future);
        }

        startLatch.countDown();

        // Collect results
        for (Future<Boolean> future : futures) {
            assertTrue(future.get(), "Time array access failed");
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void isSameDay_sameDayDifferentTime_returnsTrue() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.MARCH, 1, 9, 15, 0);
        Date date1 = cal.getTime();
        cal.set(2026, Calendar.MARCH, 1, 15, 30, 0);
        Date date2 = cal.getTime();
        assertTrue(TimeUtils.isSameDay(date1, date2));
    }

    @Test
    void isSameDay_differentDay_returnsFalse() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.MARCH, 1, 9, 15, 0);
        Date date1 = cal.getTime();
        cal.set(2026, Calendar.MARCH, 2, 9, 15, 0);
        Date date2 = cal.getTime();
        assertFalse(TimeUtils.isSameDay(date1, date2));
    }
}
