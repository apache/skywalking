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

package org.apache.skywalking.oap.server.core.analysis.indicator;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.remote.data.StreamData;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

/**
 * @author peng-yongsheng
 */
public abstract class Indicator extends StreamData implements StorageData {

    public static final String TIME_BUCKET = "time_bucket";

    @Getter @Setter @Column(columnName = TIME_BUCKET) private long timeBucket;

    public abstract String id();

    public abstract void combine(Indicator indicator);

    public abstract void calculate();

    public abstract Indicator toHour();

    public abstract Indicator toDay();

    public abstract Indicator toMonth();

    public long toTimeBucketInHour() {
        /**
         * timeBucket in minute
         *  201809120511
         * min
         *  100000000000
         * max
         *  999999999999
         */
        if (timeBucket < 999999999999L && timeBucket > 100000000000L) {
            return timeBucket / 100;
        } else {
            throw new IllegalStateException("Current time bucket is not in minute dimensionality");
        }
    }

    public long toTimeBucketInDay() {
        if (timeBucket < 999999999999L && timeBucket > 100000000000L) {
            return timeBucket / 10000;
        } else if (timeBucket < 9999999999L && timeBucket > 1000000000L) {
            return timeBucket / 100;
        } else {
            throw new IllegalStateException("Current time bucket is not in minute dimensionality");
        }
    }

    public long toTimeBucketInMonth() {
        if (timeBucket < 999999999999L && timeBucket > 100000000000L) {
            return timeBucket / 1000000;
        } else if (timeBucket < 9999999999L && timeBucket > 1000000000L) {
            return timeBucket / 10000;
        } else if (timeBucket < 99999999L && timeBucket > 10000000L) {
            return timeBucket / 100;
        } else {
            throw new IllegalStateException("Current time bucket is not in minute dimensionality");
        }
    }
}
