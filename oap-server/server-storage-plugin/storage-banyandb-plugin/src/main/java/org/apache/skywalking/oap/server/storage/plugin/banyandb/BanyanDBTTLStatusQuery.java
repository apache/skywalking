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
    private int grNormalTTLDays;
    private int grSuperTTLDays;
    // -1 means no cold stage.
    private int grColdNormalTTLDays = -1;
    private int grColdSuperTTLDays = -1;
    private int gmMinuteTTLDays;
    private int gmHourTTLDays;
    private int gmDayTTLDays;
    private int gmColdMinuteTTLDays = -1;
    private int gmColdHourTTLDays = -1;
    private int gmColdDayTTLDays = -1;

    public BanyanDBTTLStatusQuery(BanyanDBStorageConfig config) {
        grNormalTTLDays = config.getRecordsNormal().getTtl();
        grSuperTTLDays = config.getRecordsSuper().getTtl();
        gmMinuteTTLDays = config.getMetricsMin().getTtl();
        gmHourTTLDays = config.getMetricsHour().getTtl();
        gmDayTTLDays = config.getMetricsDay().getTtl();
        config.getRecordsNormal().getAdditionalLifecycleStages().forEach(stage -> {
            if (stage.getName().equals(BanyanDBStorageConfig.StageName.warm)) {
                grNormalTTLDays = grNormalTTLDays + stage.getTtl();
            } else if (stage.getName().equals(BanyanDBStorageConfig.StageName.cold)) {
                grColdNormalTTLDays = stage.getTtl();
            }
        });
        config.getRecordsSuper().getAdditionalLifecycleStages().forEach(stage -> {
            if (stage.getName().equals(BanyanDBStorageConfig.StageName.warm)) {
                grSuperTTLDays = grSuperTTLDays + stage.getTtl();
            } else if (stage.getName().equals(BanyanDBStorageConfig.StageName.cold)) {
                grColdSuperTTLDays = stage.getTtl();
            }
        });
        config.getMetricsMin().getAdditionalLifecycleStages().forEach(stage -> {
            if (stage.getName().equals(BanyanDBStorageConfig.StageName.warm)) {
                gmMinuteTTLDays = gmMinuteTTLDays + stage.getTtl();
            } else if (stage.getName().equals(BanyanDBStorageConfig.StageName.cold)) {
                gmColdMinuteTTLDays = stage.getTtl();
            }
        });
        config.getMetricsHour().getAdditionalLifecycleStages().forEach(stage -> {
            if (stage.getName().equals(BanyanDBStorageConfig.StageName.warm)) {
                gmHourTTLDays = gmHourTTLDays + stage.getTtl();
            } else if (stage.getName().equals(BanyanDBStorageConfig.StageName.cold)) {
                gmColdHourTTLDays = stage.getTtl();
            }
        });
        config.getMetricsDay().getAdditionalLifecycleStages().forEach(stage -> {
            if (stage.getName().equals(BanyanDBStorageConfig.StageName.warm)) {
                gmDayTTLDays = gmDayTTLDays + stage.getTtl();
            } else if (stage.getName().equals(BanyanDBStorageConfig.StageName.cold)) {
                gmColdDayTTLDays = stage.getTtl();
            }
        });
    }

    @Override
    public TTLDefinition getTTL() {
        TTLDefinition definition = new TTLDefinition(
            new MetricsTTL(gmMinuteTTLDays, gmHourTTLDays, gmDayTTLDays),
            new RecordsTTL(grNormalTTLDays, grSuperTTLDays)
        );
        definition.getRecords().setColdValue(grNormalTTLDays);
        definition.getRecords().setColdSuperDataset(grSuperTTLDays);
        definition.getMetrics().setColdMinute(gmColdMinuteTTLDays);
        definition.getMetrics().setColdHour(gmColdHourTTLDays);
        definition.getMetrics().setColdDay(gmColdDayTTLDays);
        return definition;
    }
}
