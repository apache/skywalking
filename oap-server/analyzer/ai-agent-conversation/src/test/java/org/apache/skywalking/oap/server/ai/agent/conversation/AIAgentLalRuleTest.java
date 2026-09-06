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

package org.apache.skywalking.oap.server.ai.agent.conversation;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javassist.ClassPool;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogDataBody;
import org.apache.skywalking.apm.network.logging.v3.LogTags;
import org.apache.skywalking.apm.network.logging.v3.TextLog;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALClassGenerator;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.filter.FilterSpec;
import org.apache.skywalking.oap.log.analyzer.v2.module.LogAnalyzerModule;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleProvider;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Digests;
import org.apache.skywalking.oap.server.ai.agent.conversation.ingest.ConversationFileBuilder;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.source.LogMetadata;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The bundled rule must compile against the builder: every extractor field is a setter on
 * {@link ConversationFileBuilder}, checked by the LAL compiler, and the rule names the layer and the output type
 * the module registers. It must also read a round from before the list attributes existed, which travels
 * without them; an absent tag reads as an empty string, and an empty string is not a number.
 */
public class AIAgentLalRuleTest {
    private static final Path RULE = Paths.get("..", "..", "server-starter", "src", "main", "resources", "lal", "ai-agent.yaml");
    private static final String CONVERSATION = "7a3c882e-0dc0-46a0-b814-6613d24b7ac2";
    private static final long SENT_AT = 1757100000000L;

    @Test
    public void theBundledRuleCompilesAgainstTheBuilder() throws Exception {
        assertNotNull(compileBundledRule());
    }

    @Test
    public void aRoundFromBeforeTheListAttributesReads() throws Exception {
        final LalExpression rule = compileBundledRule();
        final byte[] body = Fixtures.bytes(Fixtures.ROUND_FILE);

        final ConversationFileBuilder before = run(rule, round(body, false));
        assertEquals("sf", before.getFormat());
        assertEquals(Digests.sha256Hex(body), before.getDigest());
        assertEquals(Digests.countLines(body), before.getLines());
        assertEquals(CONVERSATION, before.getConversation());
        assertEquals(198L, before.getRound());
        assertEquals("2026-09-04T03:40:45.971Z", before.getSessionFromTime());
        assertEquals("", before.getTitle());
        assertEquals(0L, before.getTalks());
        assertEquals(0L, before.getSteps());
        assertEquals(0L, before.getStreams());
        assertEquals(0L, before.getSegments());
        assertEquals(0L, before.getUnresolved());

        final ConversationFileBuilder after = run(rule, round(body, true));
        assertEquals(198L, after.getRound());
        assertEquals("AI agent group/table design", after.getTitle());
        assertEquals(357L, after.getTalks());
        assertEquals(16121L, after.getSteps());
        assertEquals(132L, after.getStreams());
        assertEquals(50L, after.getSegments());
        assertEquals(0L, after.getUnresolved());
    }

    @SuppressWarnings("unchecked")
    private static LalExpression compileBundledRule() throws Exception {
        assertTrue(Files.exists(RULE), "the bundled rule is at " + RULE.toAbsolutePath());
        final Map<String, Object> yaml = new Yaml().load(new String(Files.readAllBytes(RULE), StandardCharsets.UTF_8));
        final List<Map<String, Object>> rules = (List<Map<String, Object>>) yaml.get("rules");
        assertEquals(1, rules.size());
        final Map<String, Object> rule = rules.get(0);
        assertEquals("AI_AGENT", rule.get("layer"));
        assertEquals(ConversationFileBuilder.NAME, rule.get("outputType"));

        final LALClassGenerator generator = new LALClassGenerator(new ClassPool(true));
        generator.setOutputType(ConversationFileBuilder.class);
        generator.setClassNameHint("ai_agent_rule_test");
        return generator.compile((String) rule.get("dsl"));
    }

    /**
     * Runs the rule over one record as the log analyzer does, up to the sink: the extractor fills the builder the
     * generated class installs on the context. The sink listeners are left out, so nothing is dispatched.
     */
    private static ConversationFileBuilder run(final LalExpression rule, final LogData.Builder record) throws Exception {
        final FilterSpec spec = new FilterSpec(moduleManager(), new LogAnalyzerModuleConfig());
        final Field sinkListeners = FilterSpec.class.getDeclaredField("sinkListenerFactories");
        sinkListeners.setAccessible(true);
        sinkListeners.set(spec, Collections.emptyList());
        final LogMetadata metadata = LogMetadata.builder()
                                                .service("Claude Code")
                                                .serviceInstance("wusheng@host")
                                                .layer(Layer.AI_AGENT.name())
                                                .timestamp(SENT_AT)
                                                .build();
        final ExecutionContext ctx = new ExecutionContext().init(metadata, record);
        rule.execute(spec, ctx);
        return (ConversationFileBuilder) ctx.output();
    }

    /**
     * A round as the Sessionizer pushes it: the list attributes only when its header carries them.
     */
    private static LogData.Builder round(final byte[] body, final boolean listed) {
        final LogTags.Builder tags = LogTags.newBuilder()
            .addData(tag("asz.format", "sf"))
            .addData(tag("asz.file", "_conversations/" + CONVERSATION + "/rounds/r000198-77272f5c0ed9.sf"))
            .addData(tag("asz.file.digest", Digests.sha256Hex(body)))
            .addData(tag("asz.lines", String.valueOf(Digests.countLines(body))))
            .addData(tag("asz.session", CONVERSATION))
            .addData(tag("asz.conversation", CONVERSATION))
            .addData(tag("asz.round", "198"))
            .addData(tag("asz.session.from_time", "2026-09-04T03:40:45.971Z"))
            .addData(tag("asz.session.through_time", "2026-09-04T09:17:37.559Z"));
        if (listed) {
            tags.addData(tag("asz.conversation.title", "AI agent group/table design"))
                .addData(tag("asz.conversation.talks", "357"))
                .addData(tag("asz.conversation.steps", "16121"))
                .addData(tag("asz.conversation.streams", "132"))
                .addData(tag("asz.conversation.segments", "50"))
                .addData(tag("asz.conversation.unresolved", "0"));
        }
        return LogData.newBuilder()
                      .setBody(LogDataBody.newBuilder().setText(
                          TextLog.newBuilder().setText(new String(body, StandardCharsets.UTF_8))))
                      .setTags(tags);
    }

    private static KeyStringValuePair.Builder tag(final String key, final String value) {
        return KeyStringValuePair.newBuilder().setKey(key).setValue(value);
    }

    /**
     * What the rule and its filter spec look up: the core services the sink and the builder use, and the log
     * analyzer's own provider for the metric converters.
     */
    private static ModuleManager moduleManager() {
        final ModuleManager manager = mock(ModuleManager.class);
        when(manager.find(anyString())).thenReturn(mock(ModuleProviderHolder.class));

        final ModuleProviderHolder analyzer = mock(ModuleProviderHolder.class);
        final LogAnalyzerModuleProvider provider = mock(LogAnalyzerModuleProvider.class);
        when(provider.getMetricConverts()).thenReturn(Collections.emptyList());
        when(analyzer.provider()).thenReturn(provider);
        when(manager.find(LogAnalyzerModule.NAME)).thenReturn(analyzer);

        final ModuleProviderHolder core = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder coreServices = mock(ModuleServiceHolder.class);
        when(core.provider()).thenReturn(coreServices);
        when(manager.find(CoreModule.NAME)).thenReturn(core);
        when(coreServices.getService(SourceReceiver.class)).thenReturn(mock(SourceReceiver.class));
        final ConfigService config = mock(ConfigService.class);
        when(config.getSearchableLogsTags()).thenReturn("");
        when(coreServices.getService(ConfigService.class)).thenReturn(config);
        when(coreServices.getService(NamingControl.class))
            .thenReturn(new NamingControl(70, 70, 150, new EndpointNameGrouping()));
        return manager;
    }
}
