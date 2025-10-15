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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.remote.data.StreamData;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;

/**
 * Metrics represents the statistic data, which analysis by OAL script or hard code. It has the lifecycle controlled by
 * TTL(time to live).
 */
@EqualsAndHashCode(of = {
    "timeBucket"
}, callSuper = false)
public abstract class Metrics extends StreamData implements StorageData {
    public static final String ENTITY_ID = "entity_id";

    /**
     * Time attribute
     */
    @Getter
    @Setter
    @Column(name = TIME_BUCKET)
    @ElasticSearch.EnableDocValues
    private long timeBucket;

    /**
     * The last update timestamp of the cache.
     * The `update` means it is combined with the new metrics. This update doesn't mean the database level update
     * ultimately.
     */
    @Getter
    private long lastUpdateTimestamp = 0L;

    /**
     * Merge the given metrics instance, these two must be the same metrics type.
     *
     * @param metrics to be merged
     * @return {@code true} if the combined metrics should be continuously processed. {@code false} means it should be
     * abandoned, and the implementation needs to keep the data unaltered in this case.
     */
    public abstract boolean combine(Metrics metrics);

    /**
     * Calculate the metrics final value when required.
     */
    public abstract void calculate();

    /**
     * Downsampling the metrics to hour precision.
     *
     * @return the metrics in hour precision in the clone mode.
     */
    public abstract Metrics toHour();

    /**
     * Downsampling the metrics to day precision.
     *
     * @return the metrics in day precision in the clone mode.
     */
    public abstract Metrics toDay();

    /**
     * Set the last update timestamp
     *
     * @param timestamp last update timestamp
     */
    public void setLastUpdateTimestamp(long timestamp) {
        lastUpdateTimestamp = timestamp;
    }

    /**
     * @param timestamp        of current time
     * @param expiredThreshold represents the duration between last update time and the time point removing from cache.
     * @return true means this metrics should be removed from cache.
     */
    public boolean isExpired(long timestamp, long expiredThreshold) {
        return timestamp - lastUpdateTimestamp > expiredThreshold;
    }

    public long toTimeBucketInHour() {
        if (isMinuteBucket()) {
            return timeBucket / 100;
        } else {
            throw new IllegalStateException("Current time bucket is not in minute dimensionality");
        }
    }

    public long toTimeBucketInDay() {
        if (isMinuteBucket()) {
            return timeBucket / 10000;
        } else if (isHourBucket()) {
            return timeBucket / 100;
        } else {
            throw new IllegalStateException("Current time bucket is not in minute dimensionality");
        }
    }

    /**
     * Always get the duration for this time bucket in minute.
     */
    protected long getDurationInMinute() {
        if (isMinuteBucket()) {
            return 1;
        } else if (isHourBucket()) {
            return 60;
        } else if (isDayBucket()) {
            return 24 * 60;
        }
        throw new IllegalStateException("Time bucket (" + timeBucket + ") can't be recognized.");
    }

    private boolean isMinuteBucket() {
        return TimeBucket.isMinuteBucket(timeBucket);
    }

    private boolean isHourBucket() {
        return TimeBucket.isHourBucket(timeBucket);
    }

    private boolean isDayBucket() {
        return TimeBucket.isDayBucket(timeBucket);
    }

    private volatile StorageID id;

    @Override
    public StorageID id() {
        if (id == null) {
            id = id0();
        }
        return id;
    }

    /**
     * @return {@link StorageID} of this metrics to represent the unique identity in storage.
     * This ID doesn't have to match the physical storage primary key.
     * The storage could pick another way to indicate the unique identity, such as BanyanDB is using
     * {@link BanyanDB.SeriesID}
     */
    protected abstract StorageID id0();
}
