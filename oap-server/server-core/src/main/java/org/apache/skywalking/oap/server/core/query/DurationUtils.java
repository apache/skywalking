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
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public enum DurationUtils {
    INSTANCE;

    private static final int MAX_TIME_RANGE = 500;

    private static final DateTimeFormatter YYYY_MM_DD = DateTimeFormat.forPattern("yyyy-MM-dd");
    private static final DateTimeFormatter YYYY_MM_DD_HH = DateTimeFormat.forPattern("yyyy-MM-dd HH");
    private static final DateTimeFormatter YYYY_MM_DD_HHMM = DateTimeFormat.forPattern("yyyy-MM-dd HHmm");
    private static final DateTimeFormatter YYYY_MM_DD_HHMMSS = DateTimeFormat.forPattern("yyyy-MM-dd HHmmss");

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormat.forPattern("yyyyMMdd");
    private static final DateTimeFormatter YYYYMMDDHH = DateTimeFormat.forPattern("yyyyMMddHH");
    private static final DateTimeFormatter YYYYMMDDHHMM = DateTimeFormat.forPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter YYYYMMDDHHMMSS = DateTimeFormat.forPattern("yyyyMMddHHmmss");

    @Setter
    private ConfigService configService;

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

    public long startTimeDurationToSecondTimeBucket(Step step, long startTimeBucket) {
        switch (step) {
            case DAY:
                return startTimeBucket * 100 * 100 * 100;
            case HOUR:
                return startTimeBucket * 100 * 100;
            case MINUTE:
                return startTimeBucket * 100;
            case SECOND:
                return startTimeBucket;
        }
        throw new UnexpectedException("Unsupported step " + step.name());
    }

    public long endTimeDurationToSecondTimeBucket(Step step, long endTimeBucket) {
        switch (step) {
            case DAY:
                return ((endTimeBucket * 100 + 23) * 100 + 59) * 100 + 59;
            case HOUR:
                return (endTimeBucket * 100 + 59) * 100 + 59;
            case MINUTE:
                return endTimeBucket * 100 + 59;
            case SECOND:
                return endTimeBucket;
        }
        throw new UnexpectedException("Unsupported step " + step.name());
    }

    public List<PointOfTime> getDurationPoints(Step step, long startTimeBucket, long endTimeBucket) {
        DateTime dateTime = parseToDateTime(step, startTimeBucket);

        List<PointOfTime> durations = new LinkedList<>();
        durations.add(new PointOfTime(startTimeBucket));

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

    public long startTimeToTimestamp(Step step, long time) {
        switch (step) {
            case DAY:
                return YYYYMMDD.parseMillis(String.valueOf(time));
            case HOUR:
                return YYYYMMDDHH.parseMillis(String.valueOf(time));
            case MINUTE:
                return YYYYMMDDHHMM.parseMillis(String.valueOf(time));
            case SECOND:
                return YYYYMMDDHHMMSS.parseMillis(String.valueOf(time));
        }
        throw new UnexpectedException("Unsupported step " + step.name());
    }

    public long endTimeToTimestamp(Step step, long time) {
        switch (step) {
            case DAY:
                return YYYYMMDD.parseDateTime(String.valueOf(time)).plusDays(1).getMillis() - 1;
            case HOUR:
                return YYYYMMDDHH.parseDateTime(String.valueOf(time)).plusHours(1).getMillis() - 1;
            case MINUTE:
                return YYYYMMDDHHMM.parseDateTime(String.valueOf(time)).plusMinutes(1).getMillis() - 1;
            case SECOND:
                return YYYYMMDDHHMMSS.parseDateTime(String.valueOf(time)).plusSeconds(1).getMillis() - 1;
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

    // Trim the startStr according to the TTL, for query
    public long trimToStartTimeBucket(Step step, String startStr, boolean isRecord) {
        if (configService == null) {
            throw new UnexpectedException("ConfigService can not be null, should set ConfigService first.");
        }
        int ttl = isRecord ? configService.getRecordDataTTL() : configService.getMetricsDataTTL();
        long startDate = convertToTimeBucket(step, startStr);
        long timeFloor = Long.parseLong(new DateTime().plusDays(1 - ttl).toString("yyyyMMdd"));
        switch (step) {
            case DAY:
                break;
            case HOUR:
                timeFloor = timeFloor * 100;
                break;
            case MINUTE:
                timeFloor = timeFloor * 100 * 100;
                break;
            case SECOND:
                timeFloor = timeFloor * 100 * 100 * 100;
                break;
        }

        return Math.max(timeFloor, startDate);
    }

    // Trim the endStr according to the real date, for query
    public long trimToEndTimeBucket(Step step, String endStr) {
        long endDate = convertToTimeBucket(step, endStr);
        long timeCeiling = Long.parseLong(new DateTime().toString("yyyyMMdd"));
        switch (step) {
            case DAY:
                break;
            case HOUR:
                timeCeiling = (timeCeiling * 100) + 23;
                break;
            case MINUTE:
                timeCeiling = ((timeCeiling * 100) + 23) * 100 + 59;
                break;
            case SECOND:
                timeCeiling = (((timeCeiling * 100) + 23) * 100 + 59) * 100 + 59;
                break;
        }

        return Math.min(endDate, timeCeiling);
    }
}
