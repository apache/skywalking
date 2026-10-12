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
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.skywalking.oap.server.ai.evaluation.judge.JudgeModelException;
import org.apache.skywalking.oap.server.ai.evaluation.judge.JudgeModelRequest;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 20, unit = TimeUnit.SECONDS)
class OpenAICompatibleProviderTest {
    private static final String SUCCESS_RESPONSE =
        "{\"choices\":[{\"message\":{\"content\":\"valid evaluation\"}}]}";

    private HttpServer server;
    private RawHttpPeer rawPeer;

    @AfterEach
    void tearDown() throws IOException {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (rawPeer != null) {
            rawPeer.close();
            rawPeer = null;
        }
    }

    @Test
    void shouldDefaultRequestTimeoutTo30SecondsAndWireConnectTimeout() throws Exception {
        final OpenAICompatibleProvider provider = new OpenAICompatibleProvider(validConfig());
        assertEquals(Duration.ofSeconds(30), requestTimeout(provider));
        assertEquals(Duration.ofSeconds(30), httpClient(provider).connectTimeout().orElseThrow());
    }

    @Test
    void shouldApplyConfiguredRequestTimeoutToClientConnectAndRequestBudget() throws Exception {
        final Properties config = validConfig();
        config.setProperty("request-timeout-seconds", "7");
        final OpenAICompatibleProvider provider = new OpenAICompatibleProvider(config);
        assertEquals(Duration.ofSeconds(7), requestTimeout(provider));
        assertEquals(Duration.ofSeconds(7), httpClient(provider).connectTimeout().orElseThrow());
    }

    @Test
    void shouldCapExtremeRequestTimeoutSeconds() throws Exception {
        final Properties config = validConfig();
        // Larger than Long.MAX_VALUE nanoseconds; must stay finite for Duration.toNanos().
        config.setProperty("request-timeout-seconds", "10000000000");
        final OpenAICompatibleProvider provider = new OpenAICompatibleProvider(config);
        final Duration timeout = requestTimeout(provider);
        assertTrue(timeout.getSeconds() <= TimeUnit.NANOSECONDS.toSeconds(Long.MAX_VALUE));
        assertEquals(timeout, httpClient(provider).connectTimeout().orElseThrow());
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
    void shouldTimeoutWhileWaitingForResponseHeaders() throws Exception {
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

        final long started = System.nanoTime();
        final JudgeModelException exception = assertThrows(
            JudgeModelException.class,
            () -> provider.judge(request())
        );
        final long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;

        assertEquals(JudgeModelException.Reason.TIMEOUT, exception.getReason());
        assertTrue(
            elapsedMillis >= 700L && elapsedMillis < 2_500L,
            "delayed headers should fail near the 1s budget, took " + elapsedMillis + "ms"
        );
    }

    @Test
    void shouldTimeoutWhileReadingStalledResponseBody() throws Exception {
        rawPeer = new RawHttpPeer(RawHttpPeer.Mode.STALL_BODY, 0);
        final Properties config = validConfig();
        config.setProperty("endpoint", rawPeer.uri());
        config.setProperty("request-timeout-seconds", "1");
        config.setProperty("max-retries", "0");
        // HTTP/1.1 peer: match the util tests so body-cancel closes the stalled socket.
        final OpenAICompatibleProvider provider = providerWithHttp11(config, Duration.ofSeconds(1));

        final long started = System.nanoTime();
        final JudgeModelException exception = assertThrows(
            JudgeModelException.class,
            () -> provider.judge(request())
        );
        final long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;

        assertEquals(JudgeModelException.Reason.TIMEOUT, exception.getReason());
        assertTrue(
            elapsedMillis >= 700L && elapsedMillis < 2_500L,
            "stalled body should fail near the 1s budget, took " + elapsedMillis + "ms"
        );
        // Connection close-on-cancel is covered by JdkHttpClientUtilsTest; here we only assert the
        // provider maps the stalled-body deadline to Reason.TIMEOUT within the configured budget.
    }

    @Test
    void shouldShareOneDeadlineAcrossDelayedHeadersAndStalledBody() throws Exception {
        rawPeer = new RawHttpPeer(RawHttpPeer.Mode.DELAY_THEN_STALL, 600);
        final Properties config = validConfig();
        config.setProperty("endpoint", rawPeer.uri());
        config.setProperty("request-timeout-seconds", "1");
        config.setProperty("max-retries", "0");
        final OpenAICompatibleProvider provider = providerWithHttp11(config, Duration.ofSeconds(1));

        final long started = System.nanoTime();
        final JudgeModelException exception = assertThrows(
            JudgeModelException.class,
            () -> provider.judge(request())
        );
        final long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;

        assertEquals(JudgeModelException.Reason.TIMEOUT, exception.getReason());
        // A fresh 1s body budget after the 600ms header delay would finish near 1600ms.
        assertTrue(
            elapsedMillis >= 800L && elapsedMillis < 1_500L,
            "shared deadline should end near 1s total, took " + elapsedMillis + "ms"
        );
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

    private static OpenAICompatibleProvider providerWithHttp11(final Properties config, final Duration timeout)
        throws ModuleStartException {
        final HttpClient client = HttpClient.newBuilder()
                                            .version(HttpClient.Version.HTTP_1_1)
                                            .connectTimeout(timeout)
                                            .build();
        return new OpenAICompatibleProvider(client, config);
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

    private static Duration requestTimeout(final OpenAICompatibleProvider provider) throws Exception {
        final Field field = OpenAICompatibleProvider.class.getDeclaredField("requestTimeout");
        field.setAccessible(true);
        return (Duration) field.get(provider);
    }

    private static HttpClient httpClient(final OpenAICompatibleProvider provider) throws Exception {
        final Field field = OpenAICompatibleProvider.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        return (HttpClient) field.get(provider);
    }

    /**
     * Minimal HTTP/1.1 peer so body-stall and shared-deadline cases are exercised through the provider,
     * not only through {@code JdkHttpClientUtils} unit tests.
     */
    private static final class RawHttpPeer implements AutoCloseable {
        enum Mode {
            STALL_BODY,
            DELAY_THEN_STALL
        }

        private final ServerSocket server;
        private final Thread acceptor;
        private final CountDownLatch closed = new CountDownLatch(1);
        private volatile Socket client;

        RawHttpPeer(final Mode mode, final long headerDelayMillis) throws IOException {
            server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            acceptor = new Thread(() -> serve(mode, headerDelayMillis), "ai-eval-raw-http");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        String uri() {
            return "http://127.0.0.1:" + server.getLocalPort() + '/';
        }

        boolean awaitClosed(final long timeout, final TimeUnit unit) throws InterruptedException {
            return closed.await(timeout, unit);
        }

        private void serve(final Mode mode, final long headerDelayMillis) {
            try {
                client = server.accept();
                client.setTcpNoDelay(true);
                final InputStream in = client.getInputStream();
                final OutputStream out = client.getOutputStream();
                if (!drainRequest(in)) {
                    return;
                }
                if (mode == Mode.DELAY_THEN_STALL && sleepOrClosed(in, headerDelayMillis)) {
                    return;
                }
                // Advertise more bytes than we send, then wait for the client to cancel.
                out.write(("HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: 64\r\n" +
                    "Connection: keep-alive\r\n\r\n" +
                    "ping").getBytes(StandardCharsets.US_ASCII));
                out.flush();
                waitForClose(in);
            } catch (IOException ignored) {
                closed.countDown();
            }
        }

        private boolean sleepOrClosed(final InputStream in, final long delayMs) throws IOException {
            final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMs);
            client.setSoTimeout(200);
            while (System.nanoTime() < deadline) {
                try {
                    if (in.read() < 0) {
                        closed.countDown();
                        return true;
                    }
                } catch (SocketTimeoutException ignored) {
                    // Peer is still open.
                }
            }
            client.setSoTimeout(0);
            return false;
        }

        private void waitForClose(final InputStream in) throws IOException {
            client.setSoTimeout(0);
            try {
                if (in.read() < 0) {
                    closed.countDown();
                }
            } catch (IOException e) {
                closed.countDown();
            }
        }

        private boolean drainRequest(final InputStream in) throws IOException {
            final byte[] end = new byte[]{'\r', '\n', '\r', '\n'};
            int matched = 0;
            int value;
            while ((value = in.read()) >= 0) {
                matched = value == end[matched] ? matched + 1 : 0;
                if (matched == end.length) {
                    return true;
                }
            }
            closed.countDown();
            return false;
        }

        @Override
        public void close() throws IOException {
            server.close();
            final Socket socket = client;
            if (socket != null) {
                socket.close();
            }
            acceptor.interrupt();
        }
    }
}
