/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.core.query;

import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.LinkedList;
import java.util.List;

public enum DurationUtils {
    INSTANCE;

    private static final int MAX_TIME_RANGE = 500;

    public static final DateTimeFormatter YYYY_MM_DD = DateTimeFormat.forPattern("yyyy-MM-dd");
    public static final DateTimeFormatter YYYY_MM_DD_HH = DateTimeFormat.forPattern("yyyy-MM-dd HH");
    public static final DateTimeFormatter YYYY_MM_DD_HHMM = DateTimeFormat.forPattern("yyyy-MM-dd HHmm");
    public static final DateTimeFormatter YYYY_MM_DD_HHMMSS = DateTimeFormat.forPattern("yyyy-MM-dd HHmmss");

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormat.forPattern("yyyyMMdd");
    private static final DateTimeFormatter YYYYMMDDHH = DateTimeFormat.forPattern("yyyyMMddHH");
    private static final DateTimeFormatter YYYYMMDDHHMM = DateTimeFormat.forPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter YYYYMMDDHHMMSS = DateTimeFormat.forPattern("yyyyMMddHHmmss");

    /**
     * Convert date in `yyyy-MM-dd HHmmss` style to `yyyyMMddHHmmss` no matter the precision. Such as, in day precision,
     * this covert `yyyy-MM-dd` style to `yyyyMMdd`.
     */
    public long convertToTimeBucket(Step step, String dateStr) {
        verifyDateTimeString(step, dateStr);
        dateStr = dateStr.replaceAll(Const.LINE, Const.EMPTY_STRING);
        dateStr = dateStr.replaceAll(Const.SPACE, Const.EMPTY_STRING);
        return Long.parseLong(dateStr);
    }

    public long startTimeDurationToSecondTimeBucket(Step step, String dateStr) {
        long secondTimeBucket = convertToTimeBucket(step, dateStr);
        switch (step) {
            case DAY:
                return secondTimeBucket * 100 * 100 * 100;
            case HOUR:
                return secondTimeBucket * 100 * 100;
            case MINUTE:
                return secondTimeBucket * 100;
            case SECOND:
                return secondTimeBucket;
        }
        throw new UnexpectedException("Unsupported step " + step.name());
    }

    public long endTimeDurationToSecondTimeBucket(Step step, String dateStr) {
        long secondTimeBucket = convertToTimeBucket(step, dateStr);
        switch (step) {
            case DAY:
                return ((secondTimeBucket * 100 + 23) * 100 + 59) * 100 + 59;
            case HOUR:
                return (secondTimeBucket * 100 + 59) * 100 + 59;
            case MINUTE:
                return secondTimeBucket * 100 + 59;
            case SECOND:
                return secondTimeBucket;
        }
        throw new UnexpectedException("Unsupported step " + step.name());
    }

    public long startTimeDurationToMinuteTimeBucket(Step step, String dateStr) {
        long minTimeBucket = convertToTimeBucket(step, dateStr);
        switch (step) {
            case DAY:
                return minTimeBucket * 100 * 100;
            case HOUR:
                return minTimeBucket * 100;
            case MINUTE:
                return minTimeBucket;
            case SECOND:
                return minTimeBucket / 100;
        }
        throw new UnexpectedException("Unsupported step " + step.name());
    }

    public long endTimeDurationToMinuteTimeBucket(Step step, String dateStr) {
        long minTimeBucket = convertToTimeBucket(step, dateStr);
        switch (step) {
            case DAY:
                return (minTimeBucket * 100 + 23) * 100 + 59;
            case HOUR:
                return minTimeBucket * 100 + 59;
            case MINUTE:
                return minTimeBucket;
            case SECOND:
                return minTimeBucket / 100;
        }
        throw new UnexpectedException("Unsupported step " + step.name());
    }

    public List<PointOfTime> getDurationPoints(Step step, long startTimeBucket, long endTimeBucket) {
        DateTime dateTime = parseToDateTime(step, startTimeBucket);

        List<PointOfTime> durations = new LinkedList<>();
        durations.add(new PointOfTime(startTimeBucket));
        if (startTimeBucket == endTimeBucket) {
            return durations;
        }

        int i = 0;
        do {
            switch (step) {
                case DAY:
                    dateTime = dateTime.plusDays(1);
                    String timeBucket = YYYYMMDD.print(dateTime);
                    durations.add(new PointOfTime(Long.parseLong(timeBucket)));
                    break;
                case HOUR:
                    dateTime = dateTime.plusHours(1);
                    timeBucket = YYYYMMDDHH.print(dateTime);
                    durations.add(new PointOfTime(Long.parseLong(timeBucket)));
                    break;
                case MINUTE:
                    dateTime = dateTime.plusMinutes(1);
                    timeBucket = YYYYMMDDHHMM.print(dateTime);
                    durations.add(new PointOfTime(Long.parseLong(timeBucket)));
                    break;
                case SECOND:
                    dateTime = dateTime.plusSeconds(1);
                    timeBucket = YYYYMMDDHHMMSS.print(dateTime);
                    durations.add(new PointOfTime(Long.parseLong(timeBucket)));
                    break;
            }
            i++;
            if (i > MAX_TIME_RANGE) {
                // days, hours, minutes or seconds
                String stepStr = step.name().toLowerCase() + "s";
                String errorMsg = String.format(
                        "Duration data error, the range between the start time and the end time can't exceed %d %s",
                        MAX_TIME_RANGE, stepStr);
                throw new UnexpectedException(errorMsg);
            }
        }
        while (endTimeBucket != durations.get(durations.size() - 1).getPoint());

        return durations;
    }

    public long startTimeToTimestamp(Step step, String dateStr) {
        switch (step) {
            case DAY:
                return YYYY_MM_DD.parseMillis(dateStr);
            case HOUR:
                return YYYY_MM_DD_HH.parseMillis(dateStr);
            case MINUTE:
                return YYYY_MM_DD_HHMM.parseMillis(dateStr);
            case SECOND:
                return YYYY_MM_DD_HHMMSS.parseMillis(dateStr);
        }
        throw new UnexpectedException("Unsupported step " + step.name());
    }

    public long endTimeToTimestamp(Step step, String dateStr) {
        switch (step) {
            case DAY:
                return YYYY_MM_DD.parseDateTime(dateStr).plusDays(1).getMillis();
            case HOUR:
                return YYYY_MM_DD_HH.parseDateTime(dateStr).plusHours(1).getMillis();
            case MINUTE:
                return YYYY_MM_DD_HHMM.parseDateTime(dateStr).plusMinutes(1).getMillis();
            case SECOND:
                return YYYY_MM_DD_HHMMSS.parseDateTime(dateStr).plusSeconds(1).getMillis();
        }
        throw new UnexpectedException("Unsupported step " + step.name());
    }

    public DateTime parseToDateTime(Step step, long time) {
        switch (step) {
            case DAY:
                return YYYYMMDD.parseDateTime(String.valueOf(time));
            case HOUR:
                return YYYYMMDDHH.parseDateTime(String.valueOf(time));
            case MINUTE:
                return YYYYMMDDHHMM.parseDateTime(String.valueOf(time));
            case SECOND:
                return YYYYMMDDHHMMSS.parseDateTime(String.valueOf(time));
        }
        throw new UnexpectedException("Unexpected downsampling: " + step.name());
    }

    public void verifyDateTimeString(Step step, String dateStr) {
        switch (step) {
            case DAY:
                YYYY_MM_DD.parseDateTime(dateStr);
                return;
            case HOUR:
                YYYY_MM_DD_HH.parseDateTime(dateStr);
                return;
            case MINUTE:
                YYYY_MM_DD_HHMM.parseDateTime(dateStr);
                return;
            case SECOND:
                YYYY_MM_DD_HHMMSS.parseDateTime(dateStr);
                return;
        }
        throw new UnexpectedException("Unsupported step " + step.name());
    }

    public static Duration timestamp2Duration(long startTS, long endTS) {
        Duration duration = new Duration();
        if (endTS < startTS) {
            throw new IllegalArgumentException("End time must not be before start");
        }
        DateTime startDT = new DateTime(startTS);
        DateTime endDT = new DateTime(endTS);

        long durationValue = endTS - startTS;

        if (durationValue <= 3600000) {
            duration.setStep(Step.MINUTE);
            duration.setStart(startDT.toString(DurationUtils.YYYY_MM_DD_HHMM));
            duration.setEnd(endDT.toString(DurationUtils.YYYY_MM_DD_HHMM));
        } else if (durationValue <= 86400000) {
            duration.setStep(Step.HOUR);
            duration.setStart(startDT.toString(DurationUtils.YYYY_MM_DD_HH));
            duration.setEnd(endDT.toString(DurationUtils.YYYY_MM_DD_HH));
        } else {
            duration.setStep(Step.DAY);
            duration.setStart(startDT.toString(DurationUtils.YYYY_MM_DD));
            duration.setEnd(endDT.toString(DurationUtils.YYYY_MM_DD));
        }
        return duration;
    }
}
