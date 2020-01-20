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

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.Downsampling;
import org.apache.skywalking.oap.server.core.query.entity.Step;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * @author peng-yongsheng
 */
public enum DurationUtils {
    INSTANCE;

    private static final DateTimeFormatter YYYY_MM = DateTimeFormat.forPattern("yyyy-MM");
    private static final DateTimeFormatter YYYY_MM_DD = DateTimeFormat.forPattern("yyyy-MM-dd");
    private static final DateTimeFormatter YYYY_MM_DD_HH = DateTimeFormat.forPattern("yyyy-MM-dd HH");
    private static final DateTimeFormatter YYYY_MM_DD_HHMM = DateTimeFormat.forPattern("yyyy-MM-dd HHmm");
    private static final DateTimeFormatter YYYY_MM_DD_HHMMSS = DateTimeFormat.forPattern("yyyy-MM-dd HHmmss");

    private static final DateTimeFormatter YYYYMM = DateTimeFormat.forPattern("yyyyMM");
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormat.forPattern("yyyyMMdd");
    private static final DateTimeFormatter YYYYMMDDHH = DateTimeFormat.forPattern("yyyyMMddHH");
    private static final DateTimeFormatter YYYYMMDDHHMM = DateTimeFormat.forPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter YYYYMMDDHHMMSS = DateTimeFormat.forPattern("yyyyMMddHHmmss");

    public long exchangeToTimeBucket(String dateStr) {
        dateStr = dateStr.replaceAll(Const.LINE, Const.EMPTY_STRING);
        dateStr = dateStr.replaceAll(Const.SPACE, Const.EMPTY_STRING);
        return Long.parseLong(dateStr);
    }

    public long startTimeDurationToSecondTimeBucket(Step step, String dateStr) {
        long secondTimeBucket = 0;
        switch (step) {
            case MONTH:
                secondTimeBucket = exchangeToTimeBucket(dateStr) * 100 * 100 * 100 * 100;
                break;
            case DAY:
                secondTimeBucket = exchangeToTimeBucket(dateStr) * 100 * 100 * 100;
                break;
            case HOUR:
                secondTimeBucket = exchangeToTimeBucket(dateStr) * 100 * 100;
                break;
            case MINUTE:
                secondTimeBucket = exchangeToTimeBucket(dateStr) * 100;
                break;
            case SECOND:
                secondTimeBucket = exchangeToTimeBucket(dateStr);
                break;
        }
        return secondTimeBucket;
    }

    public long endTimeDurationToSecondTimeBucket(Step step, String dateStr) {
        long secondTimeBucket = 0;
        switch (step) {
            case MONTH:
                secondTimeBucket = (((exchangeToTimeBucket(dateStr) * 100 + 99) * 100 + 99) * 100 + 99) * 100 + 99;
                break;
            case DAY:
                secondTimeBucket = ((exchangeToTimeBucket(dateStr) * 100 + 99) * 100 + 99) * 100 + 99;
                break;
            case HOUR:
                secondTimeBucket = (exchangeToTimeBucket(dateStr) * 100 + 99) * 100 + 99;
                break;
            case MINUTE:
                secondTimeBucket = exchangeToTimeBucket(dateStr) * 100 + 99;
                break;
            case SECOND:
                secondTimeBucket = exchangeToTimeBucket(dateStr);
                break;
        }
        return secondTimeBucket;
    }

    public long startTimeToTimestamp(Step step, String dateStr) {
        switch (step) {
            case MONTH:
                return YYYY_MM.parseMillis(dateStr);
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
            case MONTH:
                return YYYY_MM.parseDateTime(dateStr).plusMonths(1).getMillis();
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

    public int minutesBetween(Downsampling downsampling, DateTime dateTime) {
        switch (downsampling) {
            case Month:
                return dateTime.dayOfMonth().getMaximumValue() * 24 * 60;
            case Day:
                return 24 * 60;
            case Hour:
                return 60;
            default:
                return 1;
        }
    }

    public int secondsBetween(Downsampling downsampling, DateTime dateTime) {
        switch (downsampling) {
            case Month:
                return dateTime.dayOfMonth().getMaximumValue() * 24 * 60 * 60;
            case Day:
                return 24 * 60 * 60;
            case Hour:
                return 60 * 60;
            case Minute:
                return 60;
            default:
                return 1;
        }
    }

    public List<DurationPoint> getDurationPoints(Downsampling downsampling, long startTimeBucket,
        long endTimeBucket) {
        DateTime dateTime = parseToDateTime(downsampling, startTimeBucket);

        List<DurationPoint> durations = new LinkedList<>();
        durations.add(new DurationPoint(startTimeBucket, secondsBetween(downsampling, dateTime), minutesBetween(downsampling, dateTime)));

        int i = 0;
        do {
            switch (downsampling) {
                case Month:
                    dateTime = dateTime.plusMonths(1);
                    String timeBucket = YYYYMM.print(dateTime);
                    durations.add(new DurationPoint(Long.parseLong(timeBucket), secondsBetween(downsampling, dateTime), minutesBetween(downsampling, dateTime)));
                    break;
                case Day:
                    dateTime = dateTime.plusDays(1);
                    timeBucket = YYYYMMDD.print(dateTime);
                    durations.add(new DurationPoint(Long.parseLong(timeBucket), secondsBetween(downsampling, dateTime), minutesBetween(downsampling, dateTime)));
                    break;
                case Hour:
                    dateTime = dateTime.plusHours(1);
                    timeBucket = YYYYMMDDHH.print(dateTime);
                    durations.add(new DurationPoint(Long.parseLong(timeBucket), secondsBetween(downsampling, dateTime), minutesBetween(downsampling, dateTime)));
                    break;
                case Minute:
                    dateTime = dateTime.plusMinutes(1);
                    timeBucket = YYYYMMDDHHMM.print(dateTime);
                    durations.add(new DurationPoint(Long.parseLong(timeBucket), secondsBetween(downsampling, dateTime), minutesBetween(downsampling, dateTime)));
                    break;
                case Second:
                    dateTime = dateTime.plusSeconds(1);
                    timeBucket = YYYYMMDDHHMMSS.print(dateTime);
                    durations.add(new DurationPoint(Long.parseLong(timeBucket), secondsBetween(downsampling, dateTime), minutesBetween(downsampling, dateTime)));
                    break;
            }
            i++;
            if (i > 500) {
                throw new UnexpectedException("Duration data error, step: " + downsampling.name() + ", start: " + startTimeBucket + ", end: " + endTimeBucket);
            }
        }
        while (endTimeBucket != durations.get(durations.size() - 1).getPoint());

        return durations;
    }

    private DateTime parseToDateTime(Downsampling downsampling, long time) {
        switch (downsampling) {
            case Month:
                return YYYYMM.parseDateTime(String.valueOf(time));
            case Day:
                return YYYYMMDD.parseDateTime(String.valueOf(time));
            case Hour:
                return YYYYMMDDHH.parseDateTime(String.valueOf(time));
            case Minute:
                return YYYYMMDDHHMM.parseDateTime(String.valueOf(time));
            case Second:
                return YYYYMMDDHHMMSS.parseDateTime(String.valueOf(time));
        }
        throw new UnexpectedException("Unexpected downsampling: " + downsampling.name());
    }
}
