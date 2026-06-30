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

import java.util.Map;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageConfig.MetricsMin;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageConfig.Stage;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageConfig.StageName;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageConfig.TopN;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BanyanDBConfigDumpExtensionTest {
    @Test
    public void shouldFlattenLoadedConfig() {
        BanyanDBStorageConfig config = new BanyanDBStorageConfig();
        config.getGlobal().setTargets("a:1,b:2");
        config.getGlobal().setUser("admin");
        config.getGlobal().setPassword("admin");
        config.getGlobal().setCompatibleServerApiVersions("0.10");
        config.getGlobal().setNamespace("sw");

        // A metrics-minute group carrying a warm lifecycle stage.
        MetricsMin metricsMin = config.getMetricsMin();
        metricsMin.setShardNum(1);
        metricsMin.setTtl(7);
        metricsMin.setEnableWarmStage(true);
        Stage warm = new Stage();
        warm.setName(StageName.warm);
        warm.setTtl(30);
        metricsMin.getAdditionalLifecycleStages().add(warm);

        // A TopN rule under bydb-topn.yml.
        TopN topN = new TopN();
        topN.setCountersNumber(1000);
        topN.setSort(TopN.Sort.des);
        topN.getExcludes().add(new KeyValue("a", "b"));
        config.getTopNConfigs().put("endpoint_cpm", Map.of("endpoint_cpm-service", topN));

        Map<String, String> dump =
            new BanyanDBConfigDumpExtension("storage.banyandb", config).dumpConfigurations();

        // global: targets joined back to the raw comma form, not the split array.
        assertEquals("a:1,b:2", dump.get("storage.banyandb.global.targets"));
        // Raw values; masking is the dump's responsibility, not the extension's.
        assertEquals("admin", dump.get("storage.banyandb.global.user"));
        assertEquals("admin", dump.get("storage.banyandb.global.password"));
        assertEquals("0.10", dump.get("storage.banyandb.global.compatibleServerApiVersions"));
        // Group + indexed lifecycle stage (enum -> name()).
        assertEquals("7", dump.get("storage.banyandb.metricsMinute.ttl"));
        assertEquals("true", dump.get("storage.banyandb.metricsMinute.enableWarmStage"));
        assertEquals("warm", dump.get("storage.banyandb.metricsMinute.additionalLifecycleStages.0.name"));
        assertEquals("30", dump.get("storage.banyandb.metricsMinute.additionalLifecycleStages.0.ttl"));
        // TopN nested under topN.<metric>.<rule>.
        assertEquals("1000", dump.get("storage.banyandb.topN.endpoint_cpm.endpoint_cpm-service.countersNumber"));
        assertEquals("des", dump.get("storage.banyandb.topN.endpoint_cpm.endpoint_cpm-service.sort"));
        assertEquals("[a=b]", dump.get("storage.banyandb.topN.endpoint_cpm.endpoint_cpm-service.excludes"));
    }
}
