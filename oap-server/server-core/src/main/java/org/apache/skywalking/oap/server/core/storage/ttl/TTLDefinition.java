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

package org.apache.skywalking.oap.server.core.storage.ttl;

import lombok.Data;

/**
 * TTLDefinition defines the TTL of the data in the storage.
 */
@Data
public class TTLDefinition {
    private final MetricsTTL metrics;
    private final RecordsTTL records;

    @Override
    public String toString() {
        StringBuilder ttlDefinition = new StringBuilder();
        ttlDefinition.append("# Metrics TTL includes the definition of the TTL of the metrics-ish data in the storage,\n");
        ttlDefinition.append("# e.g.\n");
        ttlDefinition.append("# 1. The metadata of the service, instance, endpoint, topology map, etc.\n");
        ttlDefinition.append("# 2. Generated metrics data from OAL and MAL engines.\n");
        ttlDefinition.append("# 3. Banyandb storage provides Data Lifecycle Stages(Hot/Warm/Cold).\n");
        ttlDefinition.append("#\n");
        ttlDefinition.append("# TTLs for each granularity metrics are listed separately.\n");
        ttlDefinition.append("#\n");
        ttlDefinition.append("# Cover hot and warm data for BanyanDB.\n");
        ttlDefinition.append("metrics.minute=").append(metrics.getMinute()).append("\n");
        ttlDefinition.append("metrics.hour=").append(metrics.getHour()).append("\n");
        ttlDefinition.append("metrics.day=").append(metrics.getDay()).append("\n");
        ttlDefinition.append("# Cold data, '-1' represents no cold stage data.\n");
        ttlDefinition.append("metrics.minute.cold=").append(metrics.getColdMinute()).append("\n");
        ttlDefinition.append("metrics.hour.cold=").append(metrics.getColdHour()).append("\n");
        ttlDefinition.append("metrics.day.cold=").append(metrics.getColdDay()).append("\n");
        ttlDefinition.append("\n");
        ttlDefinition.append("# Records TTL includes the definition of the TTL of the records data in the storage,\n");
        ttlDefinition.append("# Records include traces, logs, sampled slow SQL statements, HTTP requests(by Rover), alarms, etc.\n");
        ttlDefinition.append("# Super dataset of records are traces and logs, which volume should be much larger.\n");
        ttlDefinition.append("#\n");
        ttlDefinition.append("# Cover hot and warm data for BanyanDB.\n");
        ttlDefinition.append("records.normal=").append(records.getNormal()).append("\n");
        ttlDefinition.append("records.trace=").append(records.getTrace()).append("\n");
        ttlDefinition.append("records.zipkinTrace=").append(records.getZipkinTrace()).append("\n");
        ttlDefinition.append("records.log=").append(records.getLog()).append("\n");
        ttlDefinition.append("records.browserErrorLog=").append(records.getBrowserErrorLog()).append("\n");
        ttlDefinition.append("# Cold data, '-1' represents no cold stage data.\n");
        ttlDefinition.append("records.normal.cold=").append(records.getColdNormal()).append("\n");
        ttlDefinition.append("records.trace.cold=").append(records.getColdTrace()).append("\n");
        ttlDefinition.append("records.zipkinTrace.cold=").append(records.getColdZipkinTrace()).append("\n");
        ttlDefinition.append("records.log.cold=").append(records.getColdLog()).append("\n");
        ttlDefinition.append("records.browserErrorLog.cold=").append(records.getColdBrowserErrorLog()).append("\n");
        return ttlDefinition.toString();
    }
}
