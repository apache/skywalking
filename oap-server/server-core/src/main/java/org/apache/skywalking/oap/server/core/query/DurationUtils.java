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
import org.apache.skywalking.oap.server.core.query.entity.Step;
import org.joda.time.*;

/**
 * @author peng-yongsheng
 */
public enum DurationUtils {
    INSTANCE;

    public long exchangeToTimeBucket(String dateStr) {
        dateStr = dateStr.replaceAll("-", Const.EMPTY_STRING);
        dateStr = dateStr.replaceAll(" ", Const.EMPTY_STRING);
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

    public int minutesBetween(Step step, long startTimeBucket, long endTimeBucket) throws ParseException {
        Date startDate = formatDate(step, startTimeBucket);
        Date endDate = formatDate(step, endTimeBucket);

        return Minutes.minutesBetween(new DateTime(startDate), new DateTime(endDate)).getMinutes();
    }

    public int minutesBetween(Step step, DateTime dateTime) {
        switch (step) {
            case MONTH:
                return dateTime.dayOfMonth().getMaximumValue() * 24 * 60;
            case DAY:
                return 24 * 60;
            case HOUR:
                return 60;
            case MINUTE:
                return 1;
            case SECOND:
                return 1;
            default:
                return 1;
        }
    }

    public int secondsBetween(Step step, long startTimeBucket, long endTimeBucket) throws ParseException {
        Date startDate = formatDate(step, startTimeBucket);
        Date endDate = formatDate(step, endTimeBucket);

        return Seconds.secondsBetween(new DateTime(startDate), new DateTime(endDate)).getSeconds();
    }

    public int secondsBetween(Step step, DateTime dateTime) {
        switch (step) {
            case MONTH:
                return dateTime.dayOfMonth().getMaximumValue() * 24 * 60 * 60;
            case DAY:
                return 24 * 60 * 60;
            case HOUR:
                return 60 * 60;
            case MINUTE:
                return 60;
            case SECOND:
                return 1;
            default:
                return 1;
        }
    }

    public List<DurationPoint> getDurationPoints(Step step, long startTimeBucket,
        long endTimeBucket) throws ParseException {
        DateTime dateTime = parseToDateTime(step, startTimeBucket);

        List<DurationPoint> durations = new LinkedList<>();
        durations.add(new DurationPoint(startTimeBucket, secondsBetween(step, dateTime), minutesBetween(step, dateTime)));

        int i = 0;
        do {
            switch (step) {
                case MONTH:
                    dateTime = dateTime.plusMonths(1);
                    String timeBucket = new SimpleDateFormat("yyyyMM").format(dateTime.toDate());
                    durations.add(new DurationPoint(Long.valueOf(timeBucket), secondsBetween(step, dateTime), minutesBetween(step, dateTime)));
                    break;
                case DAY:
                    dateTime = dateTime.plusDays(1);
                    timeBucket = new SimpleDateFormat("yyyyMMdd").format(dateTime.toDate());
                    durations.add(new DurationPoint(Long.valueOf(timeBucket), secondsBetween(step, dateTime), minutesBetween(step, dateTime)));
                    break;
                case HOUR:
                    dateTime = dateTime.plusHours(1);
                    timeBucket = new SimpleDateFormat("yyyyMMddHH").format(dateTime.toDate());
                    durations.add(new DurationPoint(Long.valueOf(timeBucket), secondsBetween(step, dateTime), minutesBetween(step, dateTime)));
                    break;
                case MINUTE:
                    dateTime = dateTime.plusMinutes(1);
                    timeBucket = new SimpleDateFormat("yyyyMMddHHmm").format(dateTime.toDate());
                    durations.add(new DurationPoint(Long.valueOf(timeBucket), secondsBetween(step, dateTime), minutesBetween(step, dateTime)));
                    break;
                case SECOND:
                    dateTime = dateTime.plusSeconds(1);
                    timeBucket = new SimpleDateFormat("yyyyMMddHHmmss").format(dateTime.toDate());
                    durations.add(new DurationPoint(Long.valueOf(timeBucket), secondsBetween(step, dateTime), minutesBetween(step, dateTime)));
                    break;
            }
            i++;
            if (i > 500) {
                throw new UnexpectedException("Duration data error, step: " + step.name() + ", start: " + startTimeBucket + ", end: " + endTimeBucket);
            }
        }
        while (endTimeBucket != durations.get(durations.size() - 1).getPoint());

        return durations;
    }

    private Date formatDate(Step step, long timeBucket) throws ParseException {
        Date date = null;
        switch (step) {
            case MONTH:
                date = new SimpleDateFormat("yyyyMM").parse(String.valueOf(timeBucket));
                break;
            case DAY:
                date = new SimpleDateFormat("yyyyMMdd").parse(String.valueOf(timeBucket));
                break;
            case HOUR:
                date = new SimpleDateFormat("yyyyMMddHH").parse(String.valueOf(timeBucket));
                break;
            case MINUTE:
                date = new SimpleDateFormat("yyyyMMddHHmm").parse(String.valueOf(timeBucket));
                break;
            case SECOND:
                date = new SimpleDateFormat("yyyyMMddHHmmss").parse(String.valueOf(timeBucket));
                break;
        }
        return date;
    }

    private DateTime parseToDateTime(Step step, long time) throws ParseException {
        DateTime dateTime = null;

        switch (step) {
            case MONTH:
                Date date = new SimpleDateFormat("yyyyMM").parse(String.valueOf(time));
                dateTime = new DateTime(date);
                break;
            case DAY:
                date = new SimpleDateFormat("yyyyMMdd").parse(String.valueOf(time));
                dateTime = new DateTime(date);
                break;
            case HOUR:
                date = new SimpleDateFormat("yyyyMMddHH").parse(String.valueOf(time));
                dateTime = new DateTime(date);
                break;
            case MINUTE:
                date = new SimpleDateFormat("yyyyMMddHHmm").parse(String.valueOf(time));
                dateTime = new DateTime(date);
                break;
            case SECOND:
                date = new SimpleDateFormat("yyyyMMddHHmmss").parse(String.valueOf(time));
                dateTime = new DateTime(date);
                break;
        }

        return dateTime;
    }
}
