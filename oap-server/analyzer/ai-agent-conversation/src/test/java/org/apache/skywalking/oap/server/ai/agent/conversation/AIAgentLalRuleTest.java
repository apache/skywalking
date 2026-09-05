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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import javassist.ClassPool;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALClassGenerator;
import org.apache.skywalking.oap.server.ai.agent.conversation.ingest.ConversationFileBuilder;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bundled rule must compile against the builder: every extractor field is a setter on
 * {@link ConversationFileBuilder}, checked by the LAL compiler, and the rule names the layer and the output type
 * the module registers.
 */
public class AIAgentLalRuleTest {
    private static final Path RULE = Paths.get("..", "..", "server-starter", "src", "main", "resources", "lal", "ai-agent.yaml");

    @Test
    @SuppressWarnings("unchecked")
    public void theBundledRuleCompilesAgainstTheBuilder() throws Exception {
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
        assertNotNull(generator.compile((String) rule.get("dsl")));
    }
}
