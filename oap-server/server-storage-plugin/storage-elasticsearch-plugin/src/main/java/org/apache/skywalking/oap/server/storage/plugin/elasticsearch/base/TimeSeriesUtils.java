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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base;

import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.Downsampling;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * TimeSeriesUtils sets up and splits the time suffix of index name.
 */
public class TimeSeriesUtils {
    private static DateTimeFormatter TIME_BUCKET_FORMATTER = DateTimeFormat.forPattern("yyyyMMdd");
    /**
     * We are far from the first day of 2000, so we set it as the day one to make sure the index based on {@link
     * #DAY_STEP} is consistently no matter whenever the OAP starts up.
     */
    private static final DateTime DAY_ONE = TIME_BUCKET_FORMATTER.parseDateTime("20000101");
    @Setter
    private static int DAY_STEP = 1;

    public static String timeSeries(Model model) {
        long timeBucket = TimeBucket.getTimeBucket(System.currentTimeMillis(), model.getDownsampling());
        return timeSeries(model, timeBucket);
    }

    public static String timeSeries(String modelName, long timeBucket, Downsampling downsampling) {
        switch (downsampling) {
            case None:
                return modelName;
            case Hour:
                return modelName + Const.LINE + compressTimeBucket(timeBucket / 100, DAY_STEP);
            case Minute:
                return modelName + Const.LINE + compressTimeBucket(timeBucket / 10000, DAY_STEP);
            case Day:
                return modelName + Const.LINE + compressTimeBucket(timeBucket, DAY_STEP);
            case Month:
                return modelName + Const.LINE + timeBucket;
            case Second:
                return modelName + Const.LINE + compressTimeBucket(timeBucket / 1000000, DAY_STEP);
            default:
                throw new UnexpectedException("Unexpected downsampling value, " + downsampling);
        }
    }

    static String timeSeries(Model model, long timeBucket) {
        if (!model.isCapableOfTimeSeries()) {
            return model.getName();
        }

        return timeSeries(model.getName(), timeBucket, model.getDownsampling());
    }

    static long indexTimeSeries(String indexName) {
        return Long.valueOf(indexName.substring(indexName.lastIndexOf(Const.LINE) + 1));
    }

    /**
     * Follow the dayStep to re-format the time bucket literal long value.
     *
     * Such as, in dayStep == 11,
     *
     * 20000105 re-formatted time bucket is 20000101, 20000115 re-formatted time bucket is 20000112, 20000123
     * re-formatted time bucket is 20000123
     */
    static long compressTimeBucket(long timeBucket, int dayStep) {
        if (dayStep > 1) {
            DateTime time = TIME_BUCKET_FORMATTER.parseDateTime("" + timeBucket);
            int days = Days.daysBetween(DAY_ONE, time).getDays();
            int groupBucketOffset = days % dayStep;
            return Long.parseLong(time.minusDays(groupBucketOffset).toString(TIME_BUCKET_FORMATTER));
        } else {
            /**
             * No calculation required. dayStep is for lower traffic. For normally configuration, there is pointless to calculate.
             */
            return timeBucket;
        }
    }
}
