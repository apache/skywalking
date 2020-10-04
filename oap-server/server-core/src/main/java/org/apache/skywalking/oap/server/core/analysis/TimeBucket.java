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
 */

package org.apache.skywalking.oap.server.core.analysis;

import java.util.Calendar;
import org.apache.skywalking.oap.server.core.UnexpectedException;

public class TimeBucket {

    /**
     * Record time bucket format in Second Unit.
     *
     * @param time Timestamp
     * @return time in second format
     */
    public static long getRecordTimeBucket(long time) {
        return getTimeBucket(time, DownSampling.Second);
    }

    /**
     * Record time bucket format in Minute Unit.
     *
     * @param time Timestamp
     * @return time in minute format
     */
    public static long getMinuteTimeBucket(long time) {
        return getTimeBucket(time, DownSampling.Minute);
    }

    /**
     * Convert TimeBucket to Timestamp in millisecond.
     *
     * @param timeBucket long
     * @return timestamp in millisecond unit
     */
    public static long getTimestamp(long timeBucket) {
        if (isSecondBucket(timeBucket)) {
            return getTimestamp(timeBucket, DownSampling.Second);
        } else if (isMinuteBucket(timeBucket)) {
            return getTimestamp(timeBucket, DownSampling.Minute);
        } else if (isHourBucket(timeBucket)) {
            return getTimestamp(timeBucket, DownSampling.Hour);
        } else if (isDayBucket(timeBucket)) {
            return getTimestamp(timeBucket, DownSampling.Day);
        } else {
            throw new UnexpectedException("Unknown downsampling value.");
        }
    }

    /**
     * The format of timeBucket in minute Unit is "yyyyMMddHHmmss", so which means the TimeBucket must be between
     * 10000000000000 and 99999999999999.
     */
    public static boolean isSecondBucket(long timeBucket) {
        return timeBucket < 99999999999999L && timeBucket > 10000000000000L;
    }

    /**
     * The format of timeBucket in minute Unit is "yyyyMMddHHmm", so which means the TimeBucket must be between
     * 100000000000 and 999999999999.
     */
    public static boolean isMinuteBucket(long timeBucket) {
        return timeBucket < 999999999999L && timeBucket > 100000000000L;
    }

    /**
     * The format of timeBucket in hour Unit is "yyyyMMddHH", so which means the TimeBucket must be between 1000000000
     * and 9999999999.
     */
    public static boolean isHourBucket(long timeBucket) {
        return timeBucket < 9999999999L && timeBucket > 1000000000L;
    }

    /**
     * The format of timeBucket in day Unit is "yyyyMMdd", so which means the TimeBucket must be between 10000000 and
     * 99999999.
     */
    public static boolean isDayBucket(long timeBucket) {
        return timeBucket < 99999999L && timeBucket > 10000000L;
    }

    /**
     * Convert TimeBucket to Timestamp in millisecond.
     *
     * @param timeBucket   long
     * @param downsampling Downsampling
     * @return timestamp in millisecond unit
     */
    public static long getTimestamp(long timeBucket, DownSampling downsampling) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.HOUR_OF_DAY, 0);

        switch (downsampling) {
            case Second:
                calendar.set(Calendar.SECOND, (int) (timeBucket % 100));
                timeBucket /= 100;
                // Fall through
            case Minute:
                calendar.set(Calendar.MINUTE, (int) (timeBucket % 100));
                timeBucket /= 100;
                // Fall through
            case Hour:
                calendar.set(Calendar.HOUR_OF_DAY, (int) (timeBucket % 100));
                timeBucket /= 100;
                // Fall through
            case Day:
                calendar.set(Calendar.DAY_OF_MONTH, (int) (timeBucket % 100));
                timeBucket /= 100;
                calendar.set(Calendar.MONTH, (int) (timeBucket % 100) - 1);
                calendar.set(Calendar.YEAR, (int) (timeBucket / 100));
                break;
            default:
                throw new UnexpectedException("Unknown downsampling value.");
        }
        return calendar.getTimeInMillis();
    }

    /**
     * Record timestamp bucket format in Downsampling Unit.
     *
     * @param timestamp    Timestamp
     * @param downsampling Downsampling
     * @return timestamp in downsampling format
     */
    public static long getTimeBucket(long timestamp, DownSampling downsampling) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);

        long year = calendar.get(Calendar.YEAR);
        long month = calendar.get(Calendar.MONTH) + 1;
        long day = calendar.get(Calendar.DAY_OF_MONTH);
        long hour = calendar.get(Calendar.HOUR_OF_DAY);
        long minute = calendar.get(Calendar.MINUTE);
        long second = calendar.get(Calendar.SECOND);

        switch (downsampling) {
            case Second:
                return year * 10000000000L + month * 100000000 + day * 1000000 + hour * 10000 + minute * 100 + second;
            case Minute:
                return year * 100000000 + month * 1000000 + day * 10000 + hour * 100 + minute;
            case Hour:
                return year * 1000000 + month * 10000 + day * 100 + hour;
            case Day:
                return year * 10000 + month * 100 + day;
            default:
                throw new UnexpectedException("Unknown downsampling value.");
        }
    }
}
