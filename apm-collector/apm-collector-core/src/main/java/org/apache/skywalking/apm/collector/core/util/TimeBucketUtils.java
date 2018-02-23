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

package org.apache.skywalking.apm.collector.core.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * @author peng-yongsheng
 */
public enum TimeBucketUtils {
    INSTANCE;

    public long getMinuteTimeBucket(long time) {
        SimpleDateFormat minuteDateFormat = new SimpleDateFormat("yyyyMMddHHmm");
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        String timeStr = minuteDateFormat.format(calendar.getTime());
        return Long.valueOf(timeStr);
    }

    public long getSecondTimeBucket(long time) {
        SimpleDateFormat secondDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        String timeStr = secondDateFormat.format(calendar.getTime());
        return Long.valueOf(timeStr);
    }

    public long getHourTimeBucket(long time) {
        SimpleDateFormat hourDateFormat = new SimpleDateFormat("yyyyMMddHH");
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        String timeStr = hourDateFormat.format(calendar.getTime()) + "00";
        return Long.valueOf(timeStr);
    }

    public long getDayTimeBucket(long time) {
        SimpleDateFormat dayDateFormat = new SimpleDateFormat("yyyyMMdd");
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        String timeStr = dayDateFormat.format(calendar.getTime()) + "0000";
        return Long.valueOf(timeStr);
    }

    public String formatMinuteTimeBucket(long minuteTimeBucket) throws ParseException {
        SimpleDateFormat minuteDateFormat = new SimpleDateFormat("yyyyMMddHHmm");
        Date date = minuteDateFormat.parse(String.valueOf(minuteTimeBucket));
        SimpleDateFormat parsedMinuteDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        return parsedMinuteDateFormat.format(date);
    }

    public long minuteToHour(long minuteBucket) {
        return minuteBucket / 100;
    }

    public long minuteToDay(long minuteBucket) {
        return minuteBucket / 100 / 100;
    }

    public long minuteToMonth(long minuteBucket) {
        return minuteBucket / 100 / 100 / 100;
    }

    public long secondToMinute(long secondBucket) {
        return secondBucket / 100;
    }

    public long secondToHour(long secondBucket) {
        return secondBucket / 100 / 100;
    }

    public long secondToDay(long secondBucket) {
        return secondBucket / 100 / 100 / 100;
    }

    public long secondToMonth(long secondBucket) {
        return secondBucket / 100 / 100 / 100 / 100;
    }
}
