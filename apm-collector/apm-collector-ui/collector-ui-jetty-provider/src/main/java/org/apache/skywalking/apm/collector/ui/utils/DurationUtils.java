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
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.collector.core.UnexpectedException;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
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

    public long durationToSecondTimeBucket(Step step, String dateStr) throws ParseException {
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

    public long secondsBetween(Step step, long start, long end) throws ParseException {
        Date startDate = null;
        Date endDate = null;
        switch (step) {
            case MONTH:
                startDate = TimeBucketUtils.MONTH_DATE_FORMAT.parse(String.valueOf(start));
                endDate = TimeBucketUtils.MONTH_DATE_FORMAT.parse(String.valueOf(end));
                break;
            case DAY:
                startDate = TimeBucketUtils.DAY_DATE_FORMAT.parse(String.valueOf(start));
                endDate = TimeBucketUtils.DAY_DATE_FORMAT.parse(String.valueOf(end));
                break;
            case HOUR:
                startDate = TimeBucketUtils.HOUR_DATE_FORMAT.parse(String.valueOf(start));
                endDate = TimeBucketUtils.HOUR_DATE_FORMAT.parse(String.valueOf(end));
                break;
            case MINUTE:
                startDate = TimeBucketUtils.MINUTE_DATE_FORMAT.parse(String.valueOf(start));
                endDate = TimeBucketUtils.MINUTE_DATE_FORMAT.parse(String.valueOf(end));
                break;
            case SECOND:
                startDate = TimeBucketUtils.SECOND_DATE_FORMAT.parse(String.valueOf(start));
                endDate = TimeBucketUtils.SECOND_DATE_FORMAT.parse(String.valueOf(end));
                break;
        }

        return Seconds.secondsBetween(new DateTime(startDate), new DateTime(endDate)).getSeconds();
    }

    public DateTime parseToDateTime(Step step, long time) throws ParseException {
        DateTime dateTime = null;

        switch (step) {
            case MONTH:
                Date date = TimeBucketUtils.MONTH_DATE_FORMAT.parse(String.valueOf(time));
                dateTime = new DateTime(date);
                break;
            case DAY:
                date = TimeBucketUtils.DAY_DATE_FORMAT.parse(String.valueOf(time));
                dateTime = new DateTime(date);
                break;
            case HOUR:
                date = TimeBucketUtils.HOUR_DATE_FORMAT.parse(String.valueOf(time));
                dateTime = new DateTime(date);
                break;
            case MINUTE:
                date = TimeBucketUtils.MINUTE_DATE_FORMAT.parse(String.valueOf(time));
                dateTime = new DateTime(date);
                break;
            case SECOND:
                date = TimeBucketUtils.SECOND_DATE_FORMAT.parse(String.valueOf(time));
                dateTime = new DateTime(date);
                break;
        }

        return dateTime;
    }

    public Long[] getDurationPoints(Step step, long start, long end) throws ParseException {
        DateTime dateTime = parseToDateTime(step, start);

        List<Long> durations = new LinkedList<>();
        durations.add(start);

        int i = 0;
        do {
            switch (step) {
                case MONTH:
                    dateTime = dateTime.plusMonths(1);
                    String timeBucket = TimeBucketUtils.MONTH_DATE_FORMAT.format(dateTime.toDate());
                    durations.add(Long.valueOf(timeBucket));
                    break;
                case DAY:
                    dateTime = dateTime.plusDays(1);
                    timeBucket = TimeBucketUtils.DAY_DATE_FORMAT.format(dateTime.toDate());
                    durations.add(Long.valueOf(timeBucket));
                    break;
                case HOUR:
                    dateTime = dateTime.plusHours(1);
                    timeBucket = TimeBucketUtils.HOUR_DATE_FORMAT.format(dateTime.toDate());
                    durations.add(Long.valueOf(timeBucket));
                    break;
                case MINUTE:
                    dateTime = dateTime.plusMinutes(1);
                    timeBucket = TimeBucketUtils.MINUTE_DATE_FORMAT.format(dateTime.toDate());
                    durations.add(Long.valueOf(timeBucket));
                    break;
                case SECOND:
                    dateTime = dateTime.plusSeconds(1);
                    timeBucket = TimeBucketUtils.SECOND_DATE_FORMAT.format(dateTime.toDate());
                    durations.add(Long.valueOf(timeBucket));
                    break;
            }
            i++;
            if (i > 500) {
                throw new UnexpectedException("Duration data error, step: " + step.name() + ", start: " + start + ", end: " + end);
            }
        }
        while (end != durations.get(durations.size() - 1));

        return durations.toArray(new Long[durations.size()]);
    }
}
