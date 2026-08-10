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

package org.apache.skywalking.oap.log.analyzer.v2.provider;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Calls {@link LALConfigs#load} — the real boot loader — rather than re-performing what it does.
 *
 * <p>This distinction is the whole point. The sibling coordinate tests build a config with
 * {@code new Yaml().loadAs(...)} and then stamp the fields themselves, mirroring the loader's
 * logic. Such a test stays green even if the loader is reverted, because it never runs it. That is
 * the same trap as tests injecting a coordinate straight into a generator, one level up: it proves
 * the shape of a value the loader may never produce.
 *
 * <p>Reading from a fixture on the classpath is what makes invoking the real loader possible —
 * {@code load} resolves its path through {@code ResourceUtils}.
 */
class LALConfigsLoaderTest {

    private static final String DIR = "loader-coordinate-test";
    private static final String RULE = "coordinate-probe";

    private static List<LALConfig> loadViaProductionLoader() throws Exception {
        // No-manager overload: with no OAP booted there is no installed ModuleManager, so the
        // DB-override resolver has nothing to query and the merge degrades to the disk baseline.
        final List<LALConfigs> configs =
            LALConfigs.load(DIR, Collections.singletonList(RULE));
        assertEquals(1, configs.size(), "expected the probe rule file to load");
        return configs.get(0).getRules();
    }

    @Test
    void theLoaderStampsEachRuleWithItsOwnEntryLine() throws Exception {
        final List<LALConfig> rules = loadViaProductionLoader();

        assertEquals(2, rules.size());
        // Lines are those of the `- name:` anchors in coordinate-probe.yaml.
        assertEquals(19, rules.get(0).getLineNo(),
            "first rule's entry line, as resolved by the loader itself");
        assertEquals(26, rules.get(1).getLineNo(),
            "the second rule must not be shifted by the first rule's multi-line dsl block");
        assertNotEquals(rules.get(0).getLineNo(), rules.get(1).getLineNo());
    }

    @Test
    void theLoaderStampsIdentityBareAndAttributionCatalogQualified() throws Exception {
        final List<LALConfig> rules = loadViaProductionLoader();

        // sourceName is what the boot route publishes as the dsl-debugging RuleKey's middle
        // component, so the catalog prefix must not leak into it. RuleKey canonicalises the
        // extension; it does not strip a catalog, and it should not have to.
        assertEquals(RULE + ".yaml", rules.get(0).getSourceName());
        assertEquals("lal/" + RULE + ".yaml", rules.get(0).getSourcePath());
        assertFalse(rules.get(0).getSourcePath().startsWith("lal/lal/"),
            "the catalog prefix must not be applied twice");
    }

    @Test
    void theRuntimeRuleRouteStampsExactlyWhatTheBootLoaderDoes() throws Exception {
        final LALConfig fromDisk = loadViaProductionLoader().get(0);

        // The runtime-rule applier hands the rule's name without an extension. Both routes go
        // through stampSource, so this asserts the two produce identical LALConfig coordinates —
        // which is what keeps a hot-updated rule's generated classes attributed like its
        // disk-loaded twin. The dsl-debugging RuleKey is a separate concern, canonicalised in
        // RuleKey itself; see RuleKeyTest.
        final LALConfig fromRuntimeRule = new LALConfig();
        LALConfigs.stampSource(fromRuntimeRule, RULE);

        assertEquals(fromDisk.getSourceName(), fromRuntimeRule.getSourceName());
        assertEquals(fromDisk.getSourcePath(), fromRuntimeRule.getSourcePath());
        assertTrue(fromRuntimeRule.getSourceName().endsWith(".yaml"),
            "runtime rule names carry no extension and must gain one");
    }
}
