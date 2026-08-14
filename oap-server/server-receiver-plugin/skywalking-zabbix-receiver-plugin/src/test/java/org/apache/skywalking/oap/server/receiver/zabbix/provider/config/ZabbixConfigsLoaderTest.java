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

package org.apache.skywalking.oap.server.receiver.zabbix.provider.config;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Calls {@link ZabbixConfigs#loadConfigs} — the real loader — rather than mirroring its stamping.
 *
 * <p>Zabbix is the case that had no coordinate at all: {@code ZabbixConfig} implements
 * {@code MetricRuleConfig} but took every default, so {@code getSourceName()} was null, and its
 * generated classes carried neither a rule file nor an {@code _L} segment. A test that stamped the
 * fields itself would have reported that as working.
 */
class ZabbixConfigsLoaderTest {

    private static final String DIR = "zabbix-loader-probe";
    private static final String RULE = "probe-agent";

    private static ZabbixConfig loadViaProductionLoader() throws Exception {
        final List<ZabbixConfig> configs =
            ZabbixConfigs.loadConfigs(DIR, Collections.singletonList(RULE));
        assertEquals(1, configs.size(), "expected the probe rule file to load");
        return configs.get(0);
    }

    @Test
    void theLoaderStampsTheRuleFilePath() throws Exception {
        final ZabbixConfig config = loadViaProductionLoader();

        assertNotNull(config.getSourcePath(),
            "a null path becomes (null:20) in every generated class name and SourceFile");
        assertEquals(DIR + "/" + RULE + ".yaml", config.getSourcePath());
        assertEquals(RULE, config.getSourceName());
    }

    @Test
    void theLoaderStampsEachMetricWithItsOwnEntryLine() throws Exception {
        final ZabbixConfig config = loadViaProductionLoader();

        assertEquals(2, config.getMetrics().size());
        // Lines of the `- name:` anchors under `metrics:` — the zabbix-specific key.
        assertEquals(20, config.getMetrics().get(0).getLineNo());
        assertEquals(22, config.getMetrics().get(1).getLineNo());
        assertNotEquals(config.getMetrics().get(0).getLineNo(),
            config.getMetrics().get(1).getLineNo());
    }
}
