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
import java.util.TimeZone;
import org.apache.skywalking.apm.collector.core.UnexpectedException;

/**
 * @author peng-yongsheng
 */
public enum TimeBucketUtils {
    INSTANCE;

    private final SimpleDateFormat dayDateFormat = new SimpleDateFormat("yyyyMMdd");
    private final SimpleDateFormat hourDateFormat = new SimpleDateFormat("yyyyMMddHH");
    private final SimpleDateFormat minuteDateFormat = new SimpleDateFormat("yyyyMMddHHmm");
    private final SimpleDateFormat secondDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");

    public long getMinuteTimeBucket(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        String timeStr = minuteDateFormat.format(calendar.getTime());
        return Long.valueOf(timeStr);
    }

    public long getSecondTimeBucket(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        String timeStr = secondDateFormat.format(calendar.getTime());
        return Long.valueOf(timeStr);
    }

    public long getHourTimeBucket(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        String timeStr = hourDateFormat.format(calendar.getTime()) + "00";
        return Long.valueOf(timeStr);
    }

    public long getDayTimeBucket(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        String timeStr = dayDateFormat.format(calendar.getTime()) + "0000";
        return Long.valueOf(timeStr);
    }

    public long changeTimeBucket2TimeStamp(String timeBucketType, long timeBucket) {
        if (TimeBucketType.SECOND.name().toLowerCase().equals(timeBucketType.toLowerCase())) {
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.YEAR, Integer.valueOf(String.valueOf(timeBucket).substring(0, 4)));
            calendar.set(Calendar.MONTH, Integer.valueOf(String.valueOf(timeBucket).substring(4, 6)) - 1);
            calendar.set(Calendar.DAY_OF_MONTH, Integer.valueOf(String.valueOf(timeBucket).substring(6, 8)));
            calendar.set(Calendar.HOUR_OF_DAY, Integer.valueOf(String.valueOf(timeBucket).substring(8, 10)));
            calendar.set(Calendar.MINUTE, Integer.valueOf(String.valueOf(timeBucket).substring(10, 12)));
            calendar.set(Calendar.SECOND, Integer.valueOf(String.valueOf(timeBucket).substring(12, 14)));
            return calendar.getTimeInMillis();
        } else if (TimeBucketType.MINUTE.name().toLowerCase().equals(timeBucketType.toLowerCase())) {
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.YEAR, Integer.valueOf(String.valueOf(timeBucket).substring(0, 4)));
            calendar.set(Calendar.MONTH, Integer.valueOf(String.valueOf(timeBucket).substring(4, 6)) - 1);
            calendar.set(Calendar.DAY_OF_MONTH, Integer.valueOf(String.valueOf(timeBucket).substring(6, 8)));
            calendar.set(Calendar.HOUR_OF_DAY, Integer.valueOf(String.valueOf(timeBucket).substring(8, 10)));
            calendar.set(Calendar.MINUTE, Integer.valueOf(String.valueOf(timeBucket).substring(10, 12)));
            return calendar.getTimeInMillis();
        } else {
            throw new UnexpectedException("time bucket type must be second or minute");
        }
    }

    public long[] getFiveSecondTimeBuckets(long secondTimeBucket) {
        long timeStamp = changeTimeBucket2TimeStamp(TimeBucketType.SECOND.name(), secondTimeBucket);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeStamp);

        long[] timeBuckets = new long[5];
        timeBuckets[0] = secondTimeBucket;
        for (int i = 0; i < 4; i++) {
            calendar.add(Calendar.SECOND, -1);
            timeBuckets[i + 1] = getSecondTimeBucket(calendar.getTimeInMillis());
        }
        return timeBuckets;
    }

    public long changeToUTCTimeBucket(long timeBucket) {
        String timeBucketStr = String.valueOf(timeBucket);

        if (TimeZone.getDefault().getID().equals("GMT+08:00") || timeBucketStr.endsWith("0000")) {
            return timeBucket;
        } else {
            return timeBucket - 800;
        }
    }

    public long addSecondForSecondTimeBucket(String timeBucketType, long timeBucket, int second) {
        if (!TimeBucketType.SECOND.name().equals(timeBucketType)) {
            throw new UnexpectedException("time bucket type must be second ");
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(changeTimeBucket2TimeStamp(timeBucketType, timeBucket));
        calendar.add(Calendar.SECOND, second);

        return getSecondTimeBucket(calendar.getTimeInMillis());
    }

    public enum TimeBucketType {
        SECOND, MINUTE, HOUR, DAY
    }
}
