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

/**
 * @author peng-yongsheng
 */
public class TimeBucket {

    public static long getSecondTimeBucket(long time) {
        return getTimeBucket(time, Downsampling.Second);
    }

    public static long getMinuteTimeBucket(long time) {
        return getTimeBucket(time, Downsampling.Minute);
    }

    public static long getTimeBucket(long time, Downsampling downsampling) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);

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
            case Month:
                return year * 100 + month;
            default:
                throw new UnexpectedException("Unknown downsampling value.");
        }
    }
}
