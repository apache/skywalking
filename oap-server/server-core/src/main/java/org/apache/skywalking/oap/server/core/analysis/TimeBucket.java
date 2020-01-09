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

import org.apache.skywalking.oap.server.core.UnexpectedException;

/**
 * @author peng-yongsheng
 */
public class TimeBucket {

    public static final int SECOND_BUCKET = 60;
    public static final int HOUR_BUCKET = 60 * 60;
    public static final int DAY_BUCKET = 60 * 60 * 24;
    public static final int MONTH_BUCKET = 60 * 60 * 24 * 30;

    /**
     * Record time bucket format in Second Unit.
     *
     * @param time Timestamp
     * @return time in second format.
     */
    public static long getRecordTimeBucket(long time) {
        return getTimeBucket(time, Downsampling.Second);
    }

    public static long getMinuteTimeBucket(long time) {
        return getTimeBucket(time, Downsampling.Minute);
    }

    public static long getTimeBucket(long time, Downsampling downsampling) {
        switch (downsampling) {
            case Second:
                return time;
            case Minute:
                return time / SECOND_BUCKET;
            case Hour:
                return time / HOUR_BUCKET;
            case Day:
                return time / DAY_BUCKET;
            case Month:
                return time / MONTH_BUCKET;
            default:
                throw new UnexpectedException("Unknown downsampling value.");
        }
    }
}
