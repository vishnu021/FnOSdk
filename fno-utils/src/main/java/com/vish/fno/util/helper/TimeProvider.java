package com.vish.fno.util.helper;

import com.vish.fno.util.TimeUtils;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Locale;

import static com.vish.fno.util.FnoConstants.DATE_FORMAT;
import static com.vish.fno.util.FnoConstants.DATE_TIME_SEC_FORMAT;

/**
 * TimeProvider - Real-time implementation of TimeSource.
 *
 * This class provides actual system time for production use.
 * Implements TimeSource interface for easy substitution with
 * BacktestTimeProvider or mock implementations in testing.
 *
 * @see TimeSource
 * @see com.vish.fno.backtest.service.BacktestTimeProvider
 */
public class TimeProvider implements TimeSource {

    @Override
    public LocalDateTime now() {
        return LocalDateTime.now();
    }

    @Override
    public Date todayDate() {
        return new Date();
    }

    @Override
    public int currentTimeStampIndex() {
        return TimeUtils.getIndexOfTimeStamp(new Date());
    }

    @Override
    public String getTodaysDateString() {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(DATE_FORMAT, Locale.ENGLISH);
        return dateFormatter.format(TimeUtils.currentTime());
    }

    @Override
    public String getCurrentStringDateTime() {
        SimpleDateFormat formatterMilliSecond = new SimpleDateFormat(DATE_TIME_SEC_FORMAT, Locale.ENGLISH);
        return formatterMilliSecond.format(TimeUtils.currentTime());
    }
}
