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

package org.apache.skywalking.oap.server.core.query;

import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.ttl.MetricsTTL;
import org.apache.skywalking.oap.server.core.storage.ttl.RecordsTTL;
import org.apache.skywalking.oap.server.core.storage.ttl.StorageTTLStatusQuery;
import org.apache.skywalking.oap.server.core.storage.ttl.TTLDefinition;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;

@RequiredArgsConstructor
public class TTLStatusQuery implements Service {
    private final ModuleManager moduleManager;
    private final int coreMetricsDataTTL;
    private final int coreRecordDataTTL;

    private StorageTTLStatusQuery storageTTLStatusQuery;

    private StorageTTLStatusQuery getStorageTTLStatusQuery() {
        if (storageTTLStatusQuery == null) {
            storageTTLStatusQuery = moduleManager.find(StorageModule.NAME)
                                                 .provider()
                                                 .getService(StorageTTLStatusQuery.class);
        }
        return storageTTLStatusQuery;
    }

    /**
     * @return effective TTL configuration values.
     */
    public TTLDefinition getTTL() {
        TTLDefinition ttlDefinition = getStorageTTLStatusQuery().getTTL();
        if (ttlDefinition == null) {
            ttlDefinition = new TTLDefinition(
                new MetricsTTL(coreMetricsDataTTL, coreMetricsDataTTL, coreMetricsDataTTL, coreMetricsDataTTL),
                new RecordsTTL(coreRecordDataTTL, coreRecordDataTTL, coreRecordDataTTL, coreRecordDataTTL, coreRecordDataTTL)
            );
        }
        return ttlDefinition;
    }

    /**
     * Get the effective TTL (in days) for a specific metrics model.
     * The returned value should include both hot and warm stage TTL,
     * representing the total period during which data is accessible.
     *
     * @param model the metrics model
     * @return TTL in days, or -1 if the storage does not customize per-model TTL
     *         (consumer falls back to core metricsDataTTL)
     */
    public int getMetricsTTL(Model model) {
        int ttl = getStorageTTLStatusQuery().getMetricsTTL(model);
        if (ttl < 0) {
            ttl = coreMetricsDataTTL;
        }
        return ttl;
    }
}
