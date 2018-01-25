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

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * @author peng-yongsheng
 */
public enum TimeBucketUtils {
    INSTANCE;

    public static final SimpleDateFormat MONTH_DATE_FORMAT = new SimpleDateFormat("yyyyMM");
    public static final SimpleDateFormat DAY_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");
    public static final SimpleDateFormat HOUR_DATE_FORMAT = new SimpleDateFormat("yyyyMMddHH");
    public static final SimpleDateFormat MINUTE_DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmm");
    public static final SimpleDateFormat SECOND_DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");

    public long getMinuteTimeBucket(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        String timeStr = MINUTE_DATE_FORMAT.format(calendar.getTime());
        return Long.valueOf(timeStr);
    }

    public long getSecondTimeBucket(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        String timeStr = SECOND_DATE_FORMAT.format(calendar.getTime());
        return Long.valueOf(timeStr);
    }

    public long getHourTimeBucket(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        String timeStr = HOUR_DATE_FORMAT.format(calendar.getTime()) + "00";
        return Long.valueOf(timeStr);
    }

    public long getDayTimeBucket(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        String timeStr = DAY_DATE_FORMAT.format(calendar.getTime()) + "0000";
        return Long.valueOf(timeStr);
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

    public enum TimeBucketType {
        SECOND, MINUTE, HOUR, DAY, MONTH
    }
}
