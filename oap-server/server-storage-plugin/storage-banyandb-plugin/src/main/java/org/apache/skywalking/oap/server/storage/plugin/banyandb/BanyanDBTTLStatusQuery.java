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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import org.apache.skywalking.oap.server.core.storage.ttl.MetricsTTL;
import org.apache.skywalking.oap.server.core.storage.ttl.RecordsTTL;
import org.apache.skywalking.oap.server.core.storage.ttl.StorageTTLStatusQuery;
import org.apache.skywalking.oap.server.core.storage.ttl.TTLDefinition;

public class BanyanDBTTLStatusQuery implements StorageTTLStatusQuery {
    private final int grNormalTTLDays;
    private final int grSuperTTLDays;
    private final int gmMinuteTTLDays;
    private final int gmHourTTLDays;
    private final int gmDayTTLDays;

    public BanyanDBTTLStatusQuery(BanyanDBStorageConfig config) {
        grNormalTTLDays = config.getRecordsNormal().getTtl();
        grSuperTTLDays = config.getRecordsSuper().getTtl();
        gmMinuteTTLDays = config.getMetricsMin().getTtl();
        gmHourTTLDays = config.getMetricsHour().getTtl();
        gmDayTTLDays = config.getMetricsDay().getTtl();
    }

    @Override
    public TTLDefinition getTTL() {
        return new TTLDefinition(
            new MetricsTTL(gmMinuteTTLDays, gmHourTTLDays, gmDayTTLDays),
            new RecordsTTL(grNormalTTLDays, grSuperTTLDays)
        );
    }
}
