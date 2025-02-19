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

package org.apache.skywalking.oap.server.core.analysis.worker;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AccessLevel;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.MetricsExtension;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.SessionCacheCallback;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;

/**
 * MetricsSessionCache is a key-value cache to hold hot metric in-memory to reduce payload to pre-read.
 * Every instance of MetricsSessionCache maps to an instance of {@link MetricsPersistentWorker}.
 *
 * There are two ways to make sure metrics in-cache,
 * 1. Metrics is read from the Database through {@link MetricsPersistentWorker}.loadFromStorage
 * 2. The built {@link InsertRequest} executed successfully.
 *
 * There are two cases to remove metrics from the cache.
 * 1. The metrics expired.
 * 2. The built {@link UpdateRequest} executed failure, which could be caused
 * (1) Database error. (2) No data updated, such as the counter of update statement is 0 in JDBC.
 *
 * @since 9.4.0 Created this from MetricsPersistentWorker.sessionCache.
 */
public class MetricsSessionCache {
    private final Map<Metrics, Metrics> sessionCache;
    @Setter(AccessLevel.PACKAGE)
    private long timeoutThreshold;
    private final boolean supportUpdate;

    public MetricsSessionCache(long timeoutThreshold, final boolean supportUpdate) {
        // Due to the cache would be updated depending on final storage implementation,
        // the map/cache could be updated concurrently.
        // Set to ConcurrentHashMap in order to avoid HashMap deadlock.
        // Since 9.3.0
        this.sessionCache = new ConcurrentHashMap<>(100);
        this.timeoutThreshold = timeoutThreshold;
        this.supportUpdate = supportUpdate;
    }

    Metrics get(Metrics metrics) {
        return sessionCache.get(metrics);
    }

    public Metrics remove(Metrics metrics) {
        return sessionCache.remove(metrics);
    }

    public void put(Metrics metrics) {
        sessionCache.put(metrics, metrics);
    }

    /**
     * This method relies on the response of database flush callback.
     * Push the data into the in-memory cache for all metrics except {@link MetricsExtension#supportUpdate()} labeled
     * as false.
     * Because those data(e.g. {@link ServiceTraffic}) is one-time writing in the whole TTL period, and some
     * database(e.g. BanyanDB) has in-memory cache at the server side to improve performance but trade off the 100%
     * eventual consistency of writing, which means database server could respond
     * {@link SessionCacheCallback#onInsertCompleted()} but ends of writing failure caused by crashing.
     * This fail-safe mechanism would require the cache of this kind of metric must be read through
     * {@link IMetricsDAO#multiGet(Model, List)} which guaranteed data existing.
     */
    public void cacheAfterFlush(Metrics metrics) {
        if (supportUpdate) {
            put(metrics);
        }
        // Don't add into the cache. Rely on multiGet from Database.
    }

    void removeExpired() {
        Iterator<Metrics> iterator = sessionCache.values().iterator();
        long timestamp = System.currentTimeMillis();
        while (iterator.hasNext()) {
            Metrics metrics = iterator.next();

            if (metrics.isExpired(timestamp, timeoutThreshold)) {
                iterator.remove();
            }
        }
    }
}
