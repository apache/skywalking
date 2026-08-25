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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.skywalking.oap.server.ai.evaluation.judge.JudgeModelException;
import org.apache.skywalking.oap.server.ai.evaluation.judge.JudgeModelRequest;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OpenAICompatibleProviderTest {
    private static final String SUCCESS_RESPONSE =
        "{\"choices\":[{\"message\":{\"content\":\"valid evaluation\"}}]}";

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

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
    void shouldRejectExcessiveRetriesOnStartup() {
        final Properties config = validConfig();
        config.setProperty("max-retries", "6");
        final ModuleStartException exception = assertThrows(
            ModuleStartException.class,
            () -> new OpenAICompatibleProvider(config)
        );
        assertTrue(exception.getMessage().contains("max-retries"));
    }

    @Test
    void shouldParseRetryAfterSecondsAndCapTheDelay() throws Exception {
        final Method method = OpenAICompatibleProvider.class.getDeclaredMethod("parseRetryAfter", String.class);
        method.setAccessible(true);

        assertEquals(0L, method.invoke(null, "0"));
        assertEquals(5_000L, method.invoke(null, "3600"));
        assertEquals(-1L, method.invoke(null, "invalid"));
    }

    @Test
    void shouldClassifyMalformedJudgeResponse() throws Exception {
        final Method method = OpenAICompatibleProvider.class.getDeclaredMethod(
            "parseSuccessfulResponse", String.class
        );
        method.setAccessible(true);

        final Exception exception = assertThrows(
            Exception.class,
            () -> method.invoke(null, "not-json")
        );
        final Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        assertTrue(cause instanceof JudgeModelException);
        assertEquals(JudgeModelException.Reason.INVALID_RESPONSE, ((JudgeModelException) cause).getReason());
    }

    @Test
    void shouldRetryRateLimitedRequest() throws Exception {
        final AtomicInteger requests = new AtomicInteger();
        startServer(exchange -> {
            if (requests.incrementAndGet() == 1) {
                exchange.getResponseHeaders().add("Retry-After", "0");
                exchange.sendResponseHeaders(429, -1);
                exchange.close();
                return;
            }
            respond(exchange, 200, SUCCESS_RESPONSE);
        });

        final OpenAICompatibleProvider provider = providerForServer();

        assertEquals("valid evaluation", provider.judge(request()).getContent());
        assertEquals(2, requests.get());
    }

    @Test
    void shouldNotRetryNonRetryableRejection() throws Exception {
        final AtomicInteger requests = new AtomicInteger();
        startServer(exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });

        final JudgeModelException exception = assertThrows(
            JudgeModelException.class,
            () -> providerForServer().judge(request())
        );

        assertEquals(JudgeModelException.Reason.REJECTED, exception.getReason());
        assertEquals(1, requests.get());
    }

    @Test
    void shouldClassifyInvalidHttpResponseBody() throws Exception {
        startServer(exchange -> respond(exchange, 200, "not-json"));

        final JudgeModelException exception = assertThrows(
            JudgeModelException.class,
            () -> providerForServer().judge(request())
        );

        assertEquals(JudgeModelException.Reason.INVALID_RESPONSE, exception.getReason());
    }

    @Test
    void shouldClassifyRequestTimeout() throws Exception {
        startServer(exchange -> {
            try {
                Thread.sleep(1_500L);
                respond(exchange, 200, SUCCESS_RESPONSE);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        final Properties config = serverConfig();
        config.setProperty("request-timeout-seconds", "1");
        config.setProperty("max-retries", "0");
        final OpenAICompatibleProvider provider = new OpenAICompatibleProvider(config);

        final JudgeModelException exception = assertThrows(
            JudgeModelException.class,
            () -> provider.judge(request())
        );

        assertEquals(JudgeModelException.Reason.TIMEOUT, exception.getReason());
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

    private void startServer(final HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
    }

    private OpenAICompatibleProvider providerForServer() throws ModuleStartException {
        return new OpenAICompatibleProvider(serverConfig());
    }

    private Properties serverConfig() {
        final Properties config = validConfig();
        config.setProperty("endpoint", "http://127.0.0.1:" + server.getAddress().getPort() + '/');
        return config;
    }

    private static JudgeModelRequest request() {
        return JudgeModelRequest.builder().systemPrompt("system").userPrompt("user").build();
    }

    private static void respond(final HttpExchange exchange, final int status, final String content)
            throws IOException {
        final byte[] body = content.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
