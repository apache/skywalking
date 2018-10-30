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

package org.apache.skywalking.oap.server.library.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import org.joda.time.LocalDateTime;

/**
 * @author peng-yongsheng
 */
public enum TimeBucketUtils {
    INSTANCE;

    public long getMinuteTimeBucket(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);

        long year = calendar.get(Calendar.YEAR);
        long month = calendar.get(Calendar.MONTH) + 1;
        long day = calendar.get(Calendar.DAY_OF_MONTH);
        long hour = calendar.get(Calendar.HOUR_OF_DAY);
        long minute = calendar.get(Calendar.MINUTE);

        return year * 100000000 + month * 1000000 + day * 10000 + hour * 100 + minute;
    }

    public long getSecondTimeBucket(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);

        long year = calendar.get(Calendar.YEAR);
        long month = calendar.get(Calendar.MONTH) + 1;
        long day = calendar.get(Calendar.DAY_OF_MONTH);
        long hour = calendar.get(Calendar.HOUR_OF_DAY);
        long minute = calendar.get(Calendar.MINUTE);
        long second = calendar.get(Calendar.SECOND);

        return year * 10000000000L + month * 100000000 + day * 1000000 + hour * 10000 + minute * 100 + second;
    }

    public long getTime(LocalDateTime time) {
        return time.getYear() * 10000000000L + time.getMonthOfYear() * 100000000 + time.getDayOfMonth() * 1000000
            + time.getHourOfDay() * 10000 + time.getMinuteOfHour() * 100 + time.getSecondOfMinute();
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
}
