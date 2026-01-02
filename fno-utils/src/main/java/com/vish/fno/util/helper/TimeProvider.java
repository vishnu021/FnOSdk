package com.vish.fno.util.helper;

import com.vish.fno.util.TimeUtils;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Locale;

import static com.vish.fno.util.FnoConstants.DATE_FORMAT;
import static com.vish.fno.util.FnoConstants.DATE_TIME_SEC_FORMAT;

public class TimeProvider {

    public LocalDateTime now() {
        return LocalDateTime.now();
    }

    public Date todayDate() {
        return new Date();
    }

    public int currentTimeStampIndex() {
        return TimeUtils.getIndexOfTimeStamp(new Date());
    }

    public String getTodaysDateString() {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(DATE_FORMAT, Locale.ENGLISH);
        return dateFormatter.format(TimeUtils.currentTime());
    }

    public String getCurrentStringDateTime() {
        SimpleDateFormat formatterMilliSecond = new SimpleDateFormat(DATE_TIME_SEC_FORMAT, Locale.ENGLISH);
        return formatterMilliSecond.format(TimeUtils.currentTime());
    }
}
