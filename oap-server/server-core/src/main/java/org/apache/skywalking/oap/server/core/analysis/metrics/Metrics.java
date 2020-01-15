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

package org.apache.skywalking.oap.server.core.analysis.metrics;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.remote.data.StreamData;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * @author peng-yongsheng
 */
public abstract class Metrics extends StreamData implements StorageData {

    public static final String TIME_BUCKET = "time_bucket";
    public static final String ENTITY_ID = "entity_id";

    @Getter
    @Setter
    @Column(columnName = TIME_BUCKET)
    private long timeBucket;
    @Getter
    @Setter
    private long survivalTime = 0L;

    public abstract String id();

    public abstract void combine(Metrics metrics);

    public abstract void calculate();

    public abstract Metrics toHour();

    public abstract Metrics toDay();

    public abstract Metrics toMonth();

    public long toTimeBucketInHour() {
        if (TimeBucket.isMinuteBucket(this.timeBucket)) {
            return timeBucket / 100;
        } else {
            throw new IllegalStateException("Current time bucket is not in minute dimensionality");
        }
    }

    public long toTimeBucketInDay() {
        if (TimeBucket.isMinuteBucket(this.timeBucket)) {
            return timeBucket / 10000;
        } else if (TimeBucket.isHourBucket(this.timeBucket)) {
            return timeBucket / 100;
        } else {
            throw new IllegalStateException("Current time bucket is not in minute dimensionality");
        }
    }

    public long toTimeBucketInMonth() {
        if (TimeBucket.isMinuteBucket(this.timeBucket)) {
            return timeBucket / 1000000;
        } else if (TimeBucket.isHourBucket(this.timeBucket)) {
            return timeBucket / 10000;
        } else if (TimeBucket.isDayBucket(this.timeBucket)) {
            return timeBucket / 100;
        } else {
            throw new IllegalStateException("Current time bucket is not in minute dimensionality");
        }
    }

    /**
     * Always get the duration for this time bucket in minute.
     *
     * @return minutes.
     */
    protected long getDurationInMinute() {
        if (TimeBucket.isMinuteBucket(this.timeBucket)) {
            return 1;
        } else if (TimeBucket.isHourBucket(this.timeBucket)) {
            return 60;
        } else if (TimeBucket.isDayBucket(this.timeBucket)) {
            return 24 * 60;
        } else {
            /*
             * In month time bucket status.
             * Usually after {@link #toTimeBucketInMonth()} called.
             */
            DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyyMM");
            int dayOfMonth = formatter.parseLocalDate(timeBucket + "").getDayOfMonth();
            return dayOfMonth * 24 * 60;
        }
    }
}
