/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking-ui
 */

package org.skywalking.apm.ui.tools;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * @author peng-yongsheng
 */
public class TimeBucketTools {
    public static String format(long milliseconds) {
        Instant instant = Instant.ofEpochMilli(milliseconds);
        LocalDateTime dateTime = LocalDateTime.ofInstant(instant, TimeZone.getDefault().toZoneId());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss SSS");
        return dateTime.format(formatter);
    }

    public static long buildToSecondTimeBucket(String sliceType, long timeBucket) {
        if (Type.MINUTE.name().toLowerCase().equals(sliceType.toLowerCase())) {
            return timeBucket * 100;
        } else if (Type.HOUR.name().toLowerCase().equals(sliceType.toLowerCase())) {
            return timeBucket * 100 * 100;
        } else if (Type.DAY.name().toLowerCase().equals(sliceType.toLowerCase())) {
            return timeBucket * 100 * 100 * 100;
        } else if (Type.SECOND.name().toLowerCase().equals(sliceType.toLowerCase())) {
            return timeBucket;
        } else {
            throw new IllegalArgumentException("slice type error");
        }
    }

    public static String buildXAxis(String timeBucketType, String timeBucket) {
        if (Type.MINUTE.name().toLowerCase().equals(timeBucketType.toLowerCase())) {
            String hourValue = timeBucket.substring(8, 10);
            String minuteValue = timeBucket.substring(10, 12);
            return hourValue + ":" + minuteValue;
        } else if (Type.HOUR.name().toLowerCase().equals(timeBucketType.toLowerCase())) {
            String dayValue = timeBucket.substring(6, 8);
            String hourValue = timeBucket.substring(8, 10);
            return dayValue + " " + hourValue;
        } else {
            String monthValue = timeBucket.substring(4, 6);
            String dayValue = timeBucket.substring(6, 8);
            return monthValue + "-" + dayValue;
        }
    }

    public enum Type {
        DAY, HOUR, MINUTE, SECOND
    }
}
