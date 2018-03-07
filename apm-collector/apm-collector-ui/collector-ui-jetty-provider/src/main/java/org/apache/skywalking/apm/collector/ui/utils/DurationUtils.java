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

package org.apache.skywalking.apm.collector.ui.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.collector.core.UnexpectedException;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.DurationPoint;
import org.joda.time.DateTime;
import org.joda.time.Seconds;

/**
 * @author peng-yongsheng
 */
public enum DurationUtils {
    INSTANCE;

    public long exchangeToTimeBucket(String dateStr) throws ParseException {
        dateStr = dateStr.replaceAll("-", Const.EMPTY_STRING);
        dateStr = dateStr.replaceAll(" ", Const.EMPTY_STRING);
        return Long.valueOf(dateStr);
    }

    public long startTimeDurationToSecondTimeBucket(Step step, String dateStr) throws ParseException {
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

    public long endTimeDurationToSecondTimeBucket(Step step, String dateStr) throws ParseException {
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

    public int secondsBetween(Step step, long startTimeBucket, long endTimeBucket) throws ParseException {
        Date startDate = null;
        Date endDate = null;
        switch (step) {
            case MONTH:
                startDate = new SimpleDateFormat("yyyyMM").parse(String.valueOf(startTimeBucket));
                endDate = new SimpleDateFormat("yyyyMM").parse(String.valueOf(endTimeBucket));
                break;
            case DAY:
                startDate = new SimpleDateFormat("yyyyMMdd").parse(String.valueOf(startTimeBucket));
                endDate = new SimpleDateFormat("yyyyMMdd").parse(String.valueOf(endTimeBucket));
                break;
            case HOUR:
                startDate = new SimpleDateFormat("yyyyMMddHH").parse(String.valueOf(startTimeBucket));
                endDate = new SimpleDateFormat("yyyyMMddHH").parse(String.valueOf(endTimeBucket));
                break;
            case MINUTE:
                startDate = new SimpleDateFormat("yyyyMMddHHmm").parse(String.valueOf(startTimeBucket));
                endDate = new SimpleDateFormat("yyyyMMddHHmm").parse(String.valueOf(endTimeBucket));
                break;
            case SECOND:
                startDate = new SimpleDateFormat("yyyyMMddHHmmss").parse(String.valueOf(startTimeBucket));
                endDate = new SimpleDateFormat("yyyyMMddHHmmss").parse(String.valueOf(endTimeBucket));
                break;
        }

        return Seconds.secondsBetween(new DateTime(startDate), new DateTime(endDate)).getSeconds();
    }

    public int secondsBetween(Step step, DateTime dateTime) throws ParseException {
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

    public DateTime parseToDateTime(Step step, long time) throws ParseException {
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

    public List<DurationPoint> getDurationPoints(Step step, long startTimeBucket,
        long endTimeBucket) throws ParseException {
        DateTime dateTime = parseToDateTime(step, startTimeBucket);

        List<DurationPoint> durations = new LinkedList<>();
        durations.add(new DurationPoint(startTimeBucket, secondsBetween(step, dateTime)));

        int i = 0;
        do {
            switch (step) {
                case MONTH:
                    dateTime = dateTime.plusMonths(1);
                    String timeBucket = new SimpleDateFormat("yyyyMM").format(dateTime.toDate());
                    durations.add(new DurationPoint(Long.valueOf(timeBucket), secondsBetween(step, dateTime)));
                    break;
                case DAY:
                    dateTime = dateTime.plusDays(1);
                    timeBucket = new SimpleDateFormat("yyyyMMdd").format(dateTime.toDate());
                    durations.add(new DurationPoint(Long.valueOf(timeBucket), secondsBetween(step, dateTime)));
                    break;
                case HOUR:
                    dateTime = dateTime.plusHours(1);
                    timeBucket = new SimpleDateFormat("yyyyMMddHH").format(dateTime.toDate());
                    durations.add(new DurationPoint(Long.valueOf(timeBucket), secondsBetween(step, dateTime)));
                    break;
                case MINUTE:
                    dateTime = dateTime.plusMinutes(1);
                    timeBucket = new SimpleDateFormat("yyyyMMddHHmm").format(dateTime.toDate());
                    durations.add(new DurationPoint(Long.valueOf(timeBucket), secondsBetween(step, dateTime)));
                    break;
                case SECOND:
                    dateTime = dateTime.plusSeconds(1);
                    timeBucket = new SimpleDateFormat("yyyyMMddHHmmss").format(dateTime.toDate());
                    durations.add(new DurationPoint(Long.valueOf(timeBucket), secondsBetween(step, dateTime)));
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
}
