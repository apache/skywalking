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
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
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

    /**
     * @return formatted latest index name, based on current timestamp.
     */
    public static String latestWriteIndexName(Model model) {
        long timeBucket;
        if (model.isRecord()) {
            timeBucket = TimeBucket.getTimeBucket(System.currentTimeMillis(), model.getDownsampling());
            return model.getName() + Const.LINE + compressTimeBucket(timeBucket / 1000000, DAY_STEP);
        } else {
            timeBucket = TimeBucket.getTimeBucket(System.currentTimeMillis(), DownSampling.Minute);
            return model.getName() + Const.LINE + compressTimeBucket(timeBucket / 10000, DAY_STEP);
        }
    }

    /**
     * @return index name based on model definition and given time bucket.
     */
    static String writeIndexName(Model model, long timeBucket) {
        final String modelName = model.getName();

        if (model.isRecord()) {
            return modelName + Const.LINE + compressTimeBucket(timeBucket / 1000000, DAY_STEP);
        } else {
            switch (model.getDownsampling()) {
                case None:
                    return modelName;
                case Hour:
                    return modelName + Const.LINE + compressTimeBucket(timeBucket / 100, DAY_STEP);
                case Minute:
                    return modelName + Const.LINE + compressTimeBucket(timeBucket / 10000, DAY_STEP);
                case Day:
                    return modelName + Const.LINE + compressTimeBucket(timeBucket, DAY_STEP);
                case Second:
                    return modelName + Const.LINE + compressTimeBucket(timeBucket / 1000000, DAY_STEP);
                default:
                    throw new UnexpectedException("Unexpected down sampling value, " + model.getDownsampling());
            }
        }
    }

    /**
     * @return index name based on model definition and given time bucket.
     */
    public static String[] traceQueryIndices(String indexName, long startSecondTB, long endSecondTB) {
        long startDay = compressTimeBucket(startSecondTB / 1000000, DAY_STEP);
        long endDay = compressTimeBucket(endSecondTB / 1000000, DAY_STEP);
        return buildTraceQueryIndices(indexName, startDay, endDay);
    }

    /**
     * @return index name based on model definition and given time bucket.
     */
    public static String[] traceIdQueryIndices(String indexName, long traceTimestamp) {
        String startTB = TIME_BUCKET_FORMATTER.print(traceTimestamp);
        long startDay = compressTimeBucket(Long.parseLong(startTB), DAY_STEP);
        long endDay = startDay + DAY_STEP;
        return buildTraceQueryIndices(indexName, startDay, endDay);
    }

    private static String[] buildTraceQueryIndices(String indexName, long startDay, long endDay) {
        final String startIndex = indexName + Const.LINE + startDay;
        if (startDay == endDay) {
            return new String[]{startIndex};
        }
        final String endIndex = indexName + Const.LINE + endDay;
        long days = endDay - startDay;
        if (days <= DAY_STEP) {
            return new String[]{startIndex, endIndex};
        }
        int steps = Math.toIntExact(days / DAY_STEP) - 1;
        String[] indices = new String[steps + 2];
        indices[0] = startIndex;
        indices[steps + 1] = endIndex;
        for (int i = 1; i <= steps; i++) {
            indices[i] = indexName + Const.LINE + (startDay + i * DAY_STEP);
        }
        return indices;
    }


    /**
     * @return the index represented time, which is included in the index name.
     */
    static long isolateTimeFromIndexName(String indexName) {
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
