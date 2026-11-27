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

import java.util.Arrays;
import java.util.List;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.annotation.Storage;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.core.storage.model.StorageModels;
import org.apache.skywalking.oap.server.core.storage.ttl.MetricsTTL;
import org.apache.skywalking.oap.server.core.storage.ttl.RecordsTTL;
import org.apache.skywalking.oap.server.core.storage.ttl.TTLDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

/**
 * The TTLs OAP reports for BanyanDB come from its group settings: the days data stays queryable by default — the hot
 * stage plus the warm stage, when there is one — and, apart, the days of the cold stage, -1 when there is none.
 * Every group is set to different days here, so a value reported for the wrong group shows.
 */
public class BanyanDBTTLStatusQueryTest {
    @Test
    public void reportsTheTTLOfEveryGroup() {
        final TTLDefinition expected = new TTLDefinition(
            new MetricsTTL(100, 7 + 14, 15 + 16, 17),
            new RecordsTTL(3 + 4, 5 + 6, 8, 9, 10 + 12, 13));
        expected.getMetrics().setColdMinute(60);
        expected.getMetrics().setColdDay(90);
        expected.getRecords().setColdNormal(30);
        expected.getRecords().setColdZipkinTrace(31);
        expected.getRecords().setColdLog(40);
        expected.getRecords().setColdBrowserErrorLog(41);

        assertEquals(expected, new BanyanDBTTLStatusQuery(config()).getTTL());
    }

    @Test
    public void reportsTheTTLOfAMetricByItsDownsampling() throws StorageException {
        final BanyanDBTTLStatusQuery query = new BanyanDBTTLStatusQuery(config());
        final List<Integer> ttls = Arrays.asList(
            query.getMetricsTTL(relationMetrics(DownSampling.Minute)),
            query.getMetricsTTL(relationMetrics(DownSampling.Hour)),
            query.getMetricsTTL(relationMetrics(DownSampling.Day)),
            query.getMetricsTTL(model(ServiceTraffic.class, DefaultScopeDefine.SERVICE, ServiceTraffic.INDEX_NAME,
                                      DownSampling.Minute)));
        assertEquals(Arrays.asList(7 + 14, 15 + 16, 17, 100), ttls, "minute, hour, day, and an index-mode metric");
    }

    @Test
    public void rejectsAMetricOfAnotherDownsampling() throws StorageException {
        final Model second = relationMetrics(DownSampling.Second);
        assertThrows(UnexpectedException.class, () -> new BanyanDBTTLStatusQuery(config()).getMetricsTTL(second));
    }

    /**
     * Every group on its own days, with each combination of the warm and the cold stage.
     */
    private static BanyanDBStorageConfig config() {
        final BanyanDBStorageConfig config = new BanyanDBStorageConfig();
        group(config.getRecordsNormal(), 3, 4, 30);
        group(config.getTrace(), 5, 6, -1);
        group(config.getZipkinTrace(), 8, -1, 31);
        group(config.getOtlpTrace(), 9, -1, -1);
        group(config.getRecordsLog(), 10, 12, 40);
        group(config.getRecordsBrowserErrorLog(), 13, -1, 41);
        group(config.getMetricsMin(), 7, 14, 60);
        group(config.getMetricsHour(), 15, 16, -1);
        group(config.getMetricsDay(), 17, -1, 90);
        group(config.getMetadata(), 100, -1, -1);
        return config;
    }

    /**
     * Sets a group's hot TTL and adds a warm or a cold stage of the given days, as the config loader does when the
     * stage is enabled; -1 leaves the stage out.
     */
    private static void group(BanyanDBStorageConfig.GroupResource group, int hot, int warm, int cold) {
        group.setTtl(hot);
        if (warm >= 0) {
            group.getAdditionalLifecycleStages().add(stage(BanyanDBStorageConfig.StageName.warm, warm));
        }
        if (cold >= 0) {
            group.getAdditionalLifecycleStages().add(stage(BanyanDBStorageConfig.StageName.cold, cold));
        }
    }

    private static BanyanDBStorageConfig.Stage stage(BanyanDBStorageConfig.StageName name, int ttl) {
        final BanyanDBStorageConfig.Stage stage = new BanyanDBStorageConfig.Stage();
        stage.setName(name);
        stage.setTtl(ttl);
        return stage;
    }

    private static Model relationMetrics(DownSampling downSampling) throws StorageException {
        return model(ServiceRelationServerSideMetrics.class, DefaultScopeDefine.SERVICE_RELATION,
                     ServiceRelationServerSideMetrics.INDEX_NAME, downSampling);
    }

    /** The model OAP builds for a metrics class at boot. */
    private static Model model(Class<?> metricsClass, int scopeId, String name, DownSampling downSampling)
        throws StorageException {
        try (MockedStatic<DefaultScopeDefine> ignored = mockStatic(DefaultScopeDefine.class)) {
            return new StorageModels().add(metricsClass, scopeId, new Storage(name, false, downSampling),
                                           StorageManipulationOpt.runtimeApply());
        }
    }
}
