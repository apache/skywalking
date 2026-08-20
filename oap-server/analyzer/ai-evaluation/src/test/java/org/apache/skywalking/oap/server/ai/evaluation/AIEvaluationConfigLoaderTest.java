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
 */

package org.apache.skywalking.oap.server.ai.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.ai.evaluation.value.ValueType;
import org.junit.jupiter.api.Test;

class AIEvaluationConfigLoaderTest {
    @Test
    void shouldMergeFileSettingsWithoutOverwritingModuleSettings() {
        final AIEvaluationConfig config = new AIEvaluationConfig();
        config.setSampleRate(123456);
        config.setBufferSize(321);
        config.setConsumerThreads(12);
        config.setMaxContentLength(8192);

        new AIEvaluationConfigLoader().load(config, Map.of(
            "judge", Map.of(
                "provider", "openai",
                "endpoint", "http://judge",
                "model", "judge-model",
                "temperature", 0.2,
                "max_tokens", 1000
            ),
            "system-prompt", "Evaluate the span.",
            "level", Map.of(
                "undefined", "unknown",
                "boolean", Map.of("true", "pass", "false", "fail")
            ),
            "tasks", List.of(Map.of(
                "name", "quality",
                "instruction", "Evaluate quality.",
                "valueType", "SCORE"
            ))
        ));

        assertEquals(123456, config.getSampleRate());
        assertEquals(321, config.getBufferSize());
        assertEquals(12, config.getConsumerThreads());
        assertEquals(8192, config.getMaxContentLength());
        assertEquals("openai", config.getJudge().getProperty("provider"));
        assertEquals("http://judge", config.getJudge().getProperty("endpoint"));
        assertEquals("0.2", String.valueOf(config.getJudge().get("temperature")));
        assertEquals("1000", String.valueOf(config.getJudge().get("max_tokens")));
        assertEquals("Evaluate the span.", config.getSystemPrompt());
        assertEquals("unknown", config.getLevel().getUndefined());
        assertEquals("pass", config.getLevel().getBooleanTrue());
        assertEquals("fail", config.getLevel().getBooleanFalse());
        assertEquals(1, config.getTasks().size());
        assertEquals("quality", config.getTasks().get(0).getName());
        assertEquals(ValueType.SCORE, config.getTasks().get(0).getValueType());
    }
}
