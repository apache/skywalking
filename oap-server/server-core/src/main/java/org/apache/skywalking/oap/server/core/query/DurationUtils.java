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

import java.text.*;
import java.util.*;

import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.analysis.Downsampling;
import org.apache.skywalking.oap.server.core.query.entity.Step;
import org.joda.time.DateTime;

/**
 * @author peng-yongsheng
 */
public enum DurationUtils {
    INSTANCE;

    public long exchangeToTimeBucket(String dateStr) {
        dateStr = dateStr.replaceAll(Const.LINE, Const.EMPTY_STRING);
        dateStr = dateStr.replaceAll(Const.SPACE, Const.EMPTY_STRING);
        return Long.valueOf(dateStr);
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

    public long startTimeToTimestamp(Step step, String dateStr) throws ParseException {
        switch (step) {
            case MONTH:
                return new SimpleDateFormat("yyyy-MM").parse(dateStr).getTime();
            case DAY:
                return new SimpleDateFormat("yyyy-MM-dd").parse(dateStr).getTime();
            case HOUR:
                return new SimpleDateFormat("yyyy-MM-dd HH").parse(dateStr).getTime();
            case MINUTE:
                return new SimpleDateFormat("yyyy-MM-dd HHmm").parse(dateStr).getTime();
            case SECOND:
                return new SimpleDateFormat("yyyy-MM-dd HHmmss").parse(dateStr).getTime();
        }
        throw new UnexpectedException("Unsupported step " + step.name());
    }

    public long endTimeToTimestamp(Step step, String dateStr) throws ParseException {
        switch (step) {
            case MONTH:
                return new DateTime(new SimpleDateFormat("yyyy-MM").parse(dateStr)).plusMonths(1).getMillis();
            case DAY:
                return new DateTime(new SimpleDateFormat("yyyy-MM-dd").parse(dateStr)).plusDays(1).getMillis();
            case HOUR:
                return new DateTime(new SimpleDateFormat("yyyy-MM-dd HH").parse(dateStr)).plusHours(1).getMillis();
            case MINUTE:
                return new DateTime(new SimpleDateFormat("yyyy-MM-dd HHmm").parse(dateStr)).plusMinutes(1).getMillis();
            case SECOND:
                return new DateTime(new SimpleDateFormat("yyyy-MM-dd HHmmss").parse(dateStr)).plusSeconds(1).getMillis();
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
        long endTimeBucket) throws ParseException {
        DateTime dateTime = parseToDateTime(downsampling, startTimeBucket);

        List<DurationPoint> durations = new LinkedList<>();
        durations.add(new DurationPoint(startTimeBucket, secondsBetween(downsampling, dateTime), minutesBetween(downsampling, dateTime)));

        int i = 0;
        do {
            switch (downsampling) {
                case Month:
                    dateTime = dateTime.plusMonths(1);
                    String timeBucket = new SimpleDateFormat("yyyyMM").format(dateTime.toDate());
                    durations.add(new DurationPoint(Long.valueOf(timeBucket), secondsBetween(downsampling, dateTime), minutesBetween(downsampling, dateTime)));
                    break;
                case Day:
                    dateTime = dateTime.plusDays(1);
                    timeBucket = new SimpleDateFormat("yyyyMMdd").format(dateTime.toDate());
                    durations.add(new DurationPoint(Long.valueOf(timeBucket), secondsBetween(downsampling, dateTime), minutesBetween(downsampling, dateTime)));
                    break;
                case Hour:
                    dateTime = dateTime.plusHours(1);
                    timeBucket = new SimpleDateFormat("yyyyMMddHH").format(dateTime.toDate());
                    durations.add(new DurationPoint(Long.valueOf(timeBucket), secondsBetween(downsampling, dateTime), minutesBetween(downsampling, dateTime)));
                    break;
                case Minute:
                    dateTime = dateTime.plusMinutes(1);
                    timeBucket = new SimpleDateFormat("yyyyMMddHHmm").format(dateTime.toDate());
                    durations.add(new DurationPoint(Long.valueOf(timeBucket), secondsBetween(downsampling, dateTime), minutesBetween(downsampling, dateTime)));
                    break;
                case Second:
                    dateTime = dateTime.plusSeconds(1);
                    timeBucket = new SimpleDateFormat("yyyyMMddHHmmss").format(dateTime.toDate());
                    durations.add(new DurationPoint(Long.valueOf(timeBucket), secondsBetween(downsampling, dateTime), minutesBetween(downsampling, dateTime)));
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

    private DateTime parseToDateTime(Downsampling downsampling, long time) throws ParseException {
        DateTime dateTime = null;

        switch (downsampling) {
            case Month:
                Date date = new SimpleDateFormat("yyyyMM").parse(String.valueOf(time));
                dateTime = new DateTime(date);
                break;
            case Day:
                date = new SimpleDateFormat("yyyyMMdd").parse(String.valueOf(time));
                dateTime = new DateTime(date);
                break;
            case Hour:
                date = new SimpleDateFormat("yyyyMMddHH").parse(String.valueOf(time));
                dateTime = new DateTime(date);
                break;
            case Minute:
                date = new SimpleDateFormat("yyyyMMddHHmm").parse(String.valueOf(time));
                dateTime = new DateTime(date);
                break;
            case Second:
                date = new SimpleDateFormat("yyyyMMddHHmmss").parse(String.valueOf(time));
                dateTime = new DateTime(date);
                break;
        }

        return dateTime;
    }

    public long convertBucketTotIimestamp(boolean isStart, long bucket) throws ParseException {
        String bucketStr = String.valueOf(bucket);
        if (bucketStr.length() < 14) {
            if (isStart) {
                if (bucketStr.length() == 6) {
                    return new SimpleDateFormat("yyyyMM").parse(bucketStr).getTime();
                } else if (bucketStr.length() == 8) {
                    return new SimpleDateFormat("yyyyMMdd").parse(bucketStr).getTime();
                } else if (bucketStr.length() == 10) {
                    return new SimpleDateFormat("yyyyMMddHH").parse(bucketStr).getTime();
                } else if (bucketStr.length() == 12) {
                    return new SimpleDateFormat("yyyyMMddHHmm").parse(bucketStr).getTime();
                }
            } else {
                if (bucketStr.length() == 6) {
                    return new DateTime(new SimpleDateFormat("yyyyMM").parse(bucketStr)).plusMonths(1).getMillis();
                } else if (bucketStr.length() == 8) {
                    return new DateTime(new SimpleDateFormat("yyyyMMdd").parse(bucketStr)).plusDays(1).getMillis();
                } else if (bucketStr.length() == 10) {
                    return new DateTime(new SimpleDateFormat("yyyyMMddHH").parse(bucketStr)).plusHours(1).getMillis();
                } else if (bucketStr.length() == 12) {
                    return new DateTime(new SimpleDateFormat("yyyyMMddHHmm").parse(bucketStr)).plusMinutes(1).getMillis();
                }
            }
        } else {
            if (isStart) {
                if (bucketStr.endsWith("00000000")) {
                    return startTimeToTimestamp(Step.MONTH, bucketStr.substring(0, 6));
                } else if (bucketStr.endsWith("000000")) {
                    return startTimeToTimestamp(Step.DAY, bucketStr.substring(0, 8));
                } else if (bucketStr.endsWith("0000")) {
                    return startTimeToTimestamp(Step.HOUR, bucketStr.substring(0, 10));
                } else if (bucketStr.endsWith("00")) {
                    return startTimeToTimestamp(Step.MINUTE, bucketStr.substring(0, 12));
                } else {
                    return startTimeToTimestamp(Step.SECOND, bucketStr.substring(0, 14));
                }

            } else {
                if (bucketStr.endsWith("99999999")) {
                    return endTimeToTimestamp(Step.MONTH, bucketStr.substring(0, 6));
                } else if (bucketStr.endsWith("999999")) {
                    return endTimeToTimestamp(Step.DAY, bucketStr.substring(0, 8));
                } else if (bucketStr.endsWith("9999")) {
                    return endTimeToTimestamp(Step.HOUR, bucketStr.substring(0, 10));
                } else if (bucketStr.endsWith("99")) {
                    return endTimeToTimestamp(Step.MINUTE, bucketStr.substring(0, 12));
                } else {
                    return endTimeToTimestamp(Step.SECOND, bucketStr.substring(0, 14));
                }
            }
        }
        return bucket;
    }
}
