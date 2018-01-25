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

package org.apache.skywalking.apm.collector.ui.utils;

import java.text.ParseException;
import java.util.Date;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.joda.time.DateTime;
import org.joda.time.Seconds;

/**
 * @author peng-yongsheng
 */
public enum DurationUtils {
    INSTANCE;

    public long exchangeToTimeBucket(String dateStr) throws ParseException {
        dateStr = dateStr.replaceAll("-", Const.EMPTY_STRING);
        dateStr = dateStr.replaceAll(" ", Const.EMPTY_STRING);
        return Long.valueOf(dateStr);
    }

    public long durationToSecondTimeBucket(Step step, String dateStr) throws ParseException {
        long secondTimeBucket = 0;
        switch (step) {
            case MONTH:
                secondTimeBucket = exchangeToTimeBucket(dateStr) * 100 * 100 * 100 * 100;
                break;
            case DAY:
                secondTimeBucket = exchangeToTimeBucket(dateStr) * 100 * 100 * 100;
                break;
            case HOUR:
                secondTimeBucket = exchangeToTimeBucket(dateStr) * 100 * 100;
                break;
            case MINUTE:
                secondTimeBucket = exchangeToTimeBucket(dateStr) * 100;
                break;
            case SECOND:
                secondTimeBucket = exchangeToTimeBucket(dateStr);
                break;
        }
        return secondTimeBucket;
    }

    public long secondsBetween(Step step, long start, long end) throws ParseException {
        Date startDate = null;
        Date endDate = null;
        switch (step) {
            case MONTH:
                startDate = TimeBucketUtils.MONTH_DATE_FORMAT.parse(String.valueOf(start));
                endDate = TimeBucketUtils.MONTH_DATE_FORMAT.parse(String.valueOf(end));
                break;
            case DAY:
                startDate = TimeBucketUtils.DAY_DATE_FORMAT.parse(String.valueOf(start));
                endDate = TimeBucketUtils.DAY_DATE_FORMAT.parse(String.valueOf(end));
                break;
            case HOUR:
                startDate = TimeBucketUtils.HOUR_DATE_FORMAT.parse(String.valueOf(start));
                endDate = TimeBucketUtils.HOUR_DATE_FORMAT.parse(String.valueOf(end));
                break;
            case MINUTE:
                startDate = TimeBucketUtils.MINUTE_DATE_FORMAT.parse(String.valueOf(start));
                endDate = TimeBucketUtils.MINUTE_DATE_FORMAT.parse(String.valueOf(end));
                break;
            case SECOND:
                startDate = TimeBucketUtils.SECOND_DATE_FORMAT.parse(String.valueOf(start));
                endDate = TimeBucketUtils.SECOND_DATE_FORMAT.parse(String.valueOf(end));
                break;
        }

        return Seconds.secondsBetween(new DateTime(startDate), new DateTime(endDate)).getSeconds();
    }
}
