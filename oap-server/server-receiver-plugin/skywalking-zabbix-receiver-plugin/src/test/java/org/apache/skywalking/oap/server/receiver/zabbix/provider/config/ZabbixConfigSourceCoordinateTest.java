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

import org.apache.skywalking.oap.server.core.dsl.DslYamlLineIndex;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Zabbix keys its rules under {@code metrics:} rather than {@code metricsRules:}, which is why it
 * fell out of the shared source-attribution path: it implements {@code MetricRuleConfig} but took
 * every default, so {@code getSourceName()} was null and its generated classes carried no rule
 * file and no {@code _L} segment at all.
 *
 * <p>The trap this pins is the one that produced the runtime-MAL {@code (null:32)} bug: Lombok's
 * {@code @Data} generates getters that SHADOW the interface defaults, so a field the loader forgets
 * to stamp returns null rather than falling back.
 */
class ZabbixConfigSourceCoordinateTest {

    private static final String YAML =
        "metricPrefix: meter_zb\n"           // 1
            + "metrics:\n"                   // 2
            + "  - name: cpu\n"              // 3
            + "    exp: agent_cpu.sum(['host'])\n"   // 4
            + "  - name: mem\n"              // 5
            + "    exp: agent_mem.sum(['host'])\n";  // 6

    /** Exactly what ZabbixConfigs.loadConfigs does after reading the file. */
    private static ZabbixConfig loadAndStamp() {
        final ZabbixConfig config = new Yaml().loadAs(YAML, ZabbixConfig.class);
        config.setSourceName("agent");
        config.setSourcePath("zabbix-rules/agent.yaml");
        final DslYamlLineIndex index = DslYamlLineIndex.index(YAML, "metrics");
        for (int i = 0; i < config.getMetrics().size(); i++) {
            config.getMetrics().get(i).setLineNo(index.rule(i).getEntryLine());
        }
        return config;
    }

    @Test
    void theRuleFileAndEachMetricLineReachTheCompiler() {
        final ZabbixConfig config = loadAndStamp();

        assertNotNull(config.getSourcePath(),
            "a null path becomes (null:3) in every generated class name and SourceFile");
        assertEquals("zabbix-rules/agent.yaml", config.getSourcePath());
        assertEquals(3, config.getMetrics().get(0).getLineNo());
        assertEquals(5, config.getMetrics().get(1).getLineNo(),
            "the metrics: key is indexed per entry, not per file");
    }

    @Test
    void anUnstampedConfigIsTheStateThatShipped() {
        // Before wiring: no sourceName, no lineNo. The generator then received a null coordinate,
        // so zabbix classes carried neither a file prefix nor an _L segment.
        final ZabbixConfig config = new Yaml().loadAs(YAML, ZabbixConfig.class);

        assertEquals(0, config.getMetrics().get(0).getLineNo());
        assertEquals(null, config.getSourceName());
    }
}
