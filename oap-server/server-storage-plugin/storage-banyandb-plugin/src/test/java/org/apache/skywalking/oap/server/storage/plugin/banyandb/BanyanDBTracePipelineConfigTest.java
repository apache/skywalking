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

import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageConfig.SamplerPluginConfig;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageConfig.TracePipeline;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BanyanDBTracePipelineConfigTest {
    private static final String ENABLED_PROP = "SW_STORAGE_BANYANDB_TRACE_PIPELINE_ENABLED";
    private static final String DURATION_THRESHOLD_PROP = "SW_STORAGE_BANYANDB_TRACE_SAMPLER_DURATION_THRESHOLD_MS";
    private static final String KEEP_TAG_RULES_PROP = "SW_STORAGE_BANYANDB_TRACE_SAMPLER_KEEP_TAG_RULES";
    private static final String ENABLED_EVENTS_PROP = "SW_STORAGE_BANYANDB_TRACE_PIPELINE_ENABLED_EVENTS";

    @AfterEach
    public void clearProps() {
        System.clearProperty(ENABLED_PROP);
        System.clearProperty(DURATION_THRESHOLD_PROP);
        System.clearProperty(KEEP_TAG_RULES_PROP);
        System.clearProperty(ENABLED_EVENTS_PROP);
    }

    private BanyanDBConfigLoader newLoader() {
        ModuleDefine moduleDefine = mock(ModuleDefine.class);
        ModuleProvider provider = mock(ModuleProvider.class);
        when(provider.name()).thenReturn("default");
        when(provider.getModule()).thenReturn(moduleDefine);
        return new BanyanDBConfigLoader(provider);
    }

    /**
     * enabledEvents has to be settable from the environment, which makes it a comma-separated
     * scalar rather than a block list. Handling only the list form left the events empty, which
     * the data node reads as MERGE for backward compatibility — so asking for FINALIZE silently
     * got MERGE, with no error to point at the ignored setting.
     */
    @Test
    public void shouldResolveEnabledEventsFromEnvironment() throws Exception {
        // Placeholder default, no override.
        TracePipeline pipeline = newLoader().loadConfig().getTrace().getTracePipeline();
        assertEquals(List.of("PIPELINE_EVENT_MERGE"), pipeline.getEnabledEvents());

        // Single override.
        System.setProperty(ENABLED_EVENTS_PROP, "PIPELINE_EVENT_FINALIZE");
        pipeline = newLoader().loadConfig().getTrace().getTracePipeline();
        assertEquals(List.of("PIPELINE_EVENT_FINALIZE"), pipeline.getEnabledEvents());

        // Both events, comma-separated, with the spacing an operator would plausibly type.
        System.setProperty(ENABLED_EVENTS_PROP, "PIPELINE_EVENT_MERGE, PIPELINE_EVENT_FINALIZE");
        pipeline = newLoader().loadConfig().getTrace().getTracePipeline();
        assertEquals(List.of("PIPELINE_EVENT_MERGE", "PIPELINE_EVENT_FINALIZE"), pipeline.getEnabledEvents());

        // An empty override yields no events rather than a blank entry, which would otherwise
        // fail later at PipelineEvent.valueOf("").
        System.setProperty(ENABLED_EVENTS_PROP, "");
        pipeline = newLoader().loadConfig().getTrace().getTracePipeline();
        assertTrue(pipeline.getEnabledEvents().isEmpty());
    }

    /**
     * The block-list form must keep working: the zipkinTrace group in the test bydb.yml still
     * uses it, so both shapes the loader accepts stay covered.
     */
    @Test
    public void shouldStillAcceptEnabledEventsAsBlockList() throws Exception {
        TracePipeline zipkin = newLoader().loadConfig().getZipkinTrace().getTracePipeline();
        assertEquals(List.of("PIPELINE_EVENT_MERGE"), zipkin.getEnabledEvents());
    }

    /**
     * The pipeline ships enabled. Pinning that here means flipping the shipped default cannot pass
     * unnoticed — it turns trace deletion on for every deployment running the plugin-capable
     * BanyanDB image, so it should never change by accident.
     */
    @Test
    public void shouldBeEnabledByDefault() throws Exception {
        BanyanDBStorageConfig config = newLoader().loadConfig();
        assertTrue(config.getTrace().getTracePipeline().isEnabled());
        assertTrue(config.getZipkinTrace().getTracePipeline().isEnabled());
    }

    @Test
    public void shouldParsePluginChainEvenWhenDisabled() throws Exception {
        System.setProperty(ENABLED_PROP, "false");
        BanyanDBStorageConfig config = newLoader().loadConfig();
        TracePipeline pipeline = config.getTrace().getTracePipeline();
        assertNotNull(pipeline);
        assertFalse(pipeline.isEnabled());
        // The chain is parsed regardless of the enabled flag; buildTracePipeline gates on enabled.
        assertEquals(1, pipeline.getPlugins().size());
        assertEquals("sw-trace-sampler.so", pipeline.getPlugins().get(0).getPath());
    }

    @Test
    public void shouldResolveEnabledAndConfigFromEnvironment() throws Exception {
        System.setProperty(ENABLED_PROP, "true");
        System.setProperty(DURATION_THRESHOLD_PROP, "250");

        BanyanDBStorageConfig config = newLoader().loadConfig();
        TracePipeline pipeline = config.getTrace().getTracePipeline();
        assertTrue(pipeline.isEnabled());
        assertEquals(List.of("PIPELINE_EVENT_MERGE"), pipeline.getEnabledEvents());
        assertEquals(1, pipeline.getPlugins().size());
        // -1 is the "not set here" sentinel: buildTracePipeline only stamps a positive value, so
        // the proto field stays unset and each data node applies its own default (30s / 5m).
        assertEquals(-1, pipeline.getMergeGraceSeconds());
        assertEquals(-1, pipeline.getFinalizeGraceSeconds());

        SamplerPluginConfig plugin = pipeline.getPlugins().get(0);
        assertEquals("sw-trace-sampler", plugin.getName());
        assertEquals("sw-trace-sampler.so", plugin.getPath());
        assertEquals("NewSampler", plugin.getSymbol());
        assertEquals(1, plugin.getAbiVersion());

        Map<String, Object> cfg = plugin.getConfig();
        // The overridden threshold (ms) flows through the ${ENV:default} placeholder into the config map as a number.
        assertEquals(250, ((Number) cfg.get("durationThresholdMs")).intValue());
        // A boolean keeps its natural type end-to-end.
        assertEquals(Boolean.TRUE, cfg.get("keepErrors"));
        // A float does NOT, when it comes from a ${ENV:default} placeholder as the shipped bydb.yml
        // writes it: the shared resolver (YamlConfigLoaderUtils.convertValueString) only preserves
        // String/Integer/Long/Boolean, so a Double falls through to its original string. The
        // first-party samplers accept a quoted number for this reason. Asserted as a String
        // deliberately — a literal 0.1 in the fixture would stay a Double and hide the real shape.
        assertEquals("0.1", cfg.get("healthySampleRate"));

        // The nested keepTagRules list of objects survives parsing as a List<Map>, not a stringified blob.
        assertTrue(cfg.get("keepTagRules") instanceof List);
        List<?> rules = (List<?>) cfg.get("keepTagRules");
        assertEquals(2, rules.size());
        Map<?, ?> rule0 = (Map<?, ?>) rules.get(0);
        assertEquals("db.type", rule0.get("tagKey"));
        assertEquals("PostgreSQL", rule0.get("equals"));
        Map<?, ?> rule1 = (Map<?, ?>) rules.get(1);
        assertEquals("mq.queue", rule1.get("tagKey"));
        assertEquals("queue-songs-ping", rule1.get("equals"));
    }

    // keepTagRules is a list of objects, so it can only be overridden by an environment
    // variable holding a one-line YAML/JSON flow sequence. convertValueString keeps an
    // ArrayList verbatim, so the nested structure survives placeholder resolution and
    // reaches the plugin as a real list rather than a stringified blob.
    @Test
    public void shouldOverrideKeepTagRulesFromEnvironment() throws Exception {
        System.setProperty(KEEP_TAG_RULES_PROP, "[{tagKey: http.method, in: [GET, POST]}]");

        BanyanDBStorageConfig config = newLoader().loadConfig();
        Map<String, Object> cfg = config.getTrace().getTracePipeline().getPlugins().get(0).getConfig();

        assertTrue(cfg.get("keepTagRules") instanceof List);
        List<?> rules = (List<?>) cfg.get("keepTagRules");
        assertEquals(1, rules.size(), "the env override must replace the in-file default rules");
        Map<?, ?> rule = (Map<?, ?>) rules.get(0);
        assertEquals("http.method", rule.get("tagKey"));
        assertEquals(List.of("GET", "POST"), rule.get("in"), "a nested list inside a rule survives too");
    }

    // The compact keepTagRules grammar ("key=value,key=~regex,key") must reach the plugin as
    // a plain string: the plugin parses it itself. convertValueString runs the resolved value
    // through SnakeYAML, so this guards against it being coerced into some other type — the
    // same trap that turns a float default into a String.
    @Test
    public void shouldPassCompactKeepTagRulesThroughAsString() throws Exception {
        final String compact = "db.type=PostgreSQL,http.status_code=~5\\d{2,3},mq.queue";
        System.setProperty(KEEP_TAG_RULES_PROP, compact);

        BanyanDBStorageConfig config = newLoader().loadConfig();
        Object rules = config.getTrace().getTracePipeline().getPlugins().get(0).getConfig().get("keepTagRules");

        assertTrue(rules instanceof String, "compact rules must stay a String, but were " + rules.getClass());
        // Verbatim: the regex quantifier's comma and the backslashes must survive untouched,
        // otherwise the plugin would parse a different rule set than the operator wrote.
        assertEquals(compact, rules);
    }

    @Test
    public void shouldParseZipkinPipelineWithNestedRules() throws Exception {
        BanyanDBStorageConfig config = newLoader().loadConfig();
        TracePipeline pipeline = config.getZipkinTrace().getTracePipeline();
        assertNotNull(pipeline);
        assertEquals(1, pipeline.getPlugins().size());

        SamplerPluginConfig plugin = pipeline.getPlugins().get(0);
        assertEquals("zipkin-trace-sampler", plugin.getName());
        assertEquals("zipkin-trace-sampler.so", plugin.getPath());

        // The fixture leaves finalizeGraceSeconds blank. A blank YAML value parses to null, which
        // Properties (Hashtable-backed) rejects, so this used to abort startup with an NPE; the
        // loader now skips it and the field keeps its unset default.
        assertEquals(0, pipeline.getFinalizeGraceSeconds());

        Map<String, Object> cfg = plugin.getConfig();
        assertEquals(1000, ((Number) cfg.get("durationThresholdMs")).intValue());
        // The zipkin fixture keeps healthySampleRate as a LITERAL where the trace group uses a
        // ${ENV:default} placeholder, so the two together cover both shapes: a literal is typed by
        // the YAML parser and stays a Double, a placeholder resolves to a String.
        assertEquals(0.05, ((Number) cfg.get("healthySampleRate")).doubleValue(), 1e-9);

        assertTrue(cfg.get("keepTagRules") instanceof List);
        List<?> rules = (List<?>) cfg.get("keepTagRules");
        assertEquals(2, rules.size());
        Map<?, ?> queryRule = (Map<?, ?>) rules.get(0);
        assertEquals("query", queryRule.get("tagKey"));
        assertEquals("http\\.status_code=5\\d\\d", queryRule.get("regex"));
        Map<?, ?> endpointRule = (Map<?, ?>) rules.get(1);
        assertEquals("local_endpoint_service_name", endpointRule.get("tagKey"));
        assertEquals("gateway.sample-services", endpointRule.get("equals"));
    }
}
