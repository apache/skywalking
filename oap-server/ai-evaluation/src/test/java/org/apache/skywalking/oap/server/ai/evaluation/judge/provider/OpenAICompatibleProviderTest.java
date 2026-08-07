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

package org.apache.skywalking.oap.server.ai.evaluation.judge.provider;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Properties;
import org.apache.skywalking.oap.server.ai.evaluation.judge.JudgeModelRequest;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.junit.jupiter.api.Test;

class OpenAICompatibleProviderTest {

    @Test
    void shouldRejectInvalidTemperatureOnStartup() {
        final Properties config = validConfig();
        config.setProperty("temperature", "3.1");
        final ModuleStartException exception = assertThrows(
            ModuleStartException.class,
            () -> new OpenAICompatibleProvider(config)
        );
        assertTrue(exception.getMessage().contains("temperature"));
    }

    @Test
    void shouldRejectInvalidMaxTokensOnStartup() {
        final Properties config = validConfig();
        config.setProperty("max_tokens", "0");
        final ModuleStartException exception = assertThrows(
            ModuleStartException.class,
            () -> new OpenAICompatibleProvider(config)
        );
        assertTrue(exception.getMessage().contains("max_tokens"));
    }

    @Test
    void shouldRejectInvalidRequestTimeoutOnStartup() {
        final Properties config = validConfig();
        config.setProperty("request-timeout-seconds", "0");
        final ModuleStartException exception = assertThrows(
            ModuleStartException.class,
            () -> new OpenAICompatibleProvider(config)
        );
        assertTrue(exception.getMessage().contains("request-timeout-seconds"));
    }

    @Test
    void shouldIncludeTemperatureAndMaxTokensInRequestBody() throws Exception {
        final Properties config = validConfig();
        config.setProperty("request-timeout-seconds", "45");
        config.setProperty("temperature", "0.25");
        config.setProperty("max_tokens", "2048");
        final OpenAICompatibleProvider provider = new OpenAICompatibleProvider(config);
        final Method method = OpenAICompatibleProvider.class.getDeclaredMethod(
            "buildRequestBody",
            JudgeModelRequest.class
        );
        method.setAccessible(true);
        final String body = (String) method.invoke(
            provider,
            JudgeModelRequest.builder()
                             .systemPrompt("sys")
                             .userPrompt("usr")
                             .build()
        );
        assertTrue(body.contains("\"temperature\":0.25"));
        assertTrue(body.contains("\"max_tokens\":2048"));
        assertTrue(body.contains("\"model\":\"gpt-5.4-mini\""));
    }

    private static Properties validConfig() {
        final Properties config = new Properties();
        config.setProperty("endpoint", "https://example.com/v1/chat/completions");
        config.setProperty("model", "gpt-5.4-mini");
        config.setProperty("api-key", "test-key");
        return config;
    }
}
