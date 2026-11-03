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

package org.apache.skywalking.oap.server.core.alarm;

import com.sun.net.httpserver.HttpServer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;
import org.apache.skywalking.oap.server.telemetry.none.MetricsCreatorNoop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpAlarmCallbackTest {

    private static HttpServer SERVER;
    private static CapturingAppender APPENDER;

    /**
     * {@link HttpAlarmCallback#post} reports delivery only through the log, so the status handling can only be
     * observed by capturing what it logs.
     */
    private static class CapturingAppender extends AbstractAppender {
        private final List<LogEvent> events = new CopyOnWriteArrayList<>();

        CapturingAppender() {
            super("capturing", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }
    }

    private static final class Callback extends HttpAlarmCallback {
        Callback() {
            this(Duration.ofSeconds(12));
        }

        Callback(final Duration timeout) {
            this(timeout, new AlarmHttpDeliveryMetrics(new MetricsCreatorNoop()), "webhook");
        }

        Callback(final Duration timeout, final AlarmHttpDeliveryMetrics metrics, final String hookType) {
            super(HttpAlarmCallback.newHttpClient(timeout), timeout, metrics, hookType);
        }

        @Override
        protected void doAlarmCallback(List<AlarmMessage> alarmMessages, boolean isRecovery) {
        }

        String send(int status) throws IOException, InterruptedException {
            return post(
                URI.create("http://127.0.0.1:" + SERVER.getAddress().getPort() + "/status/" + status),
                "{}", Map.of()
            );
        }

        String send(final URI uri) throws IOException, InterruptedException {
            return post(uri, "{}", Map.of());
        }

        String sendUnrecorded(final int status) throws IOException, InterruptedException {
            return send(
                URI.create("http://127.0.0.1:" + SERVER.getAddress().getPort() + "/status/" + status),
                "{}", Map.of()
            ).body();
        }
    }

    private static final class RecordingCreator extends MetricsCreatorNoop {
        private final AtomicInteger deliveryIncrements = new AtomicInteger();
        private final AtomicInteger statusIncrements = new AtomicInteger();
        private final List<String> deliveryKeys = new CopyOnWriteArrayList<>();
        private final List<String> statusKeys = new CopyOnWriteArrayList<>();
        private final List<Double> latencies = new ArrayList<>();

        @Override
        public CounterMetrics createCounter(final String name, final String tips, final MetricsTag.Keys tagKeys,
                                            final MetricsTag.Values tagValues) {
            final String joined = String.join(",", tagValues.getValues());
            return new CounterMetrics() {
                @Override
                public void inc() {
                    if ("alarm_http_delivery_total".equals(name)) {
                        deliveryIncrements.incrementAndGet();
                        deliveryKeys.add(joined);
                    } else if ("alarm_http_delivery_status_total".equals(name)) {
                        statusIncrements.incrementAndGet();
                        statusKeys.add(joined);
                    }
                }

                @Override
                public void inc(final double value) {
                    inc();
                }
            };
        }

        @Override
        public HistogramMetrics createHistogramMetric(final String name, final String tips,
                                                      final MetricsTag.Keys tagKeys,
                                                      final MetricsTag.Values tagValues, final double... buckets) {
            return new HistogramMetrics() {
                @Override
                public void observe(final double value) {
                    latencies.add(value);
                }
            };
        }
    }

    @BeforeAll
    public static void setUp() throws IOException {
        SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        SERVER.createContext("/status", exchange -> {
            // Drain the request body before responding. Leaving a POST unread can RST the
            // connection on Windows and flake later cases that open their own HttpClient.
            try (InputStream in = exchange.getRequestBody()) {
                in.readAllBytes();
            }
            final String path = exchange.getRequestURI().getPath();
            final int status = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
            final byte[] body = "{\"status\":\"success\"}".getBytes();
            exchange.sendResponseHeaders(status, status == 204 ? -1 : body.length);
            if (status != 204) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        SERVER.start();

        APPENDER = new CapturingAppender();
        APPENDER.start();
        ((Logger) LoggerContext.getContext(false).getLogger(Callback.class.getName())).addAppender(APPENDER);
    }

    @AfterAll
    public static void tearDown() {
        ((Logger) LoggerContext.getContext(false).getLogger(Callback.class.getName())).removeAppender(APPENDER);
        APPENDER.stop();
        SERVER.stop(0);
    }

    @BeforeEach
    public void reset() {
        APPENDER.events.clear();
    }

    @Test
    public void acceptedStatusesAreNotLoggedAsFailure() throws Exception {
        // 202 is what an asynchronous intake API such as PagerDuty's Events API v2 answers with
        for (int status : new int[] {
            200,
            201,
            202,
            204
        }) {
            new Callback().send(status);
        }
        assertTrue(
            APPENDER.events.isEmpty(),
            "a 2xx response means the hook accepted the alarm and must not be logged as a failure, but got: "
                + APPENDER.events
        );
    }

    @Test
    public void nonSuccessStatusIsLoggedAsFailure() throws Exception {
        new Callback().send(500);
        assertEquals(1, APPENDER.events.size());
        assertEquals(Level.ERROR, APPENDER.events.get(0).getLevel());
        assertTrue(APPENDER.events.get(0).getMessage().getFormattedMessage().contains("500"));
    }

    @Test
    public void successAndServerErrorAreRecorded() throws Exception {
        final RecordingCreator creator = new RecordingCreator();
        final AlarmHttpDeliveryMetrics metrics = new AlarmHttpDeliveryMetrics(creator);
        final Callback callback = new Callback(Duration.ofSeconds(12), metrics, "webhook");

        callback.send(202);
        callback.send(500);

        assertEquals(2, creator.deliveryIncrements.get());
        assertEquals(2, creator.statusIncrements.get());
        assertEquals(2, creator.latencies.size());
        assertTrue(creator.deliveryKeys.stream().anyMatch(k -> k.contains("success")));
        assertTrue(creator.deliveryKeys.stream().anyMatch(k -> k.contains("server_error")));
        assertTrue(creator.statusKeys.stream().anyMatch(k -> k.contains("2xx")));
        assertTrue(creator.statusKeys.stream().anyMatch(k -> k.contains("5xx")));
    }

    @Test
    public void sendDoesNotRecord() throws Exception {
        final RecordingCreator creator = new RecordingCreator();
        final AlarmHttpDeliveryMetrics metrics = new AlarmHttpDeliveryMetrics(creator);
        final Callback callback = new Callback(Duration.ofSeconds(12), metrics, "welink");

        callback.sendUnrecorded(200);
        callback.send(200);

        assertEquals(1, creator.deliveryIncrements.get());
        assertEquals(1, creator.statusIncrements.get());
        assertEquals(1, creator.latencies.size());
        assertTrue(creator.deliveryKeys.stream().anyMatch(k -> k.contains("welink") && k.contains("success")));
    }

    @Test
    public void eachPostIncrementsDeliveryCounterOnce() throws Exception {
        final RecordingCreator creator = new RecordingCreator();
        final AlarmHttpDeliveryMetrics metrics = new AlarmHttpDeliveryMetrics(creator);
        final Callback callback = new Callback(Duration.ofSeconds(12), metrics, "webhook");

        for (int i = 0; i < 3; i++) {
            callback.send(200);
        }
        callback.send(500);

        assertEquals(4, creator.deliveryIncrements.get());
        assertEquals(3, creator.deliveryKeys.stream().filter(k -> k.contains("success")).count());
        assertEquals(1, creator.deliveryKeys.stream().filter(k -> k.contains("server_error")).count());
        assertEquals(4, creator.statusIncrements.get());
        assertEquals(4, creator.latencies.size());
    }

    @Test
    public void nonSuccessStatusesAreRecordedByReason() throws Exception {
        final RecordingCreator creator = new RecordingCreator();
        final Callback callback = new Callback(
            Duration.ofSeconds(12), new AlarmHttpDeliveryMetrics(creator), "webhook");

        for (final int status : new int[] {401, 403, 429, 400, 404, 503}) {
            callback.send(status);
        }

        assertEquals(List.of(
            "webhook,auth_failed", "webhook,auth_failed", "webhook,rate_limited",
            "webhook,rejected", "webhook,rejected", "webhook,server_error"
        ), creator.deliveryKeys);
        assertEquals(List.of(
            "webhook,4xx", "webhook,4xx", "webhook,4xx", "webhook,4xx", "webhook,4xx", "webhook,5xx"
        ), creator.statusKeys);
    }

    @Test
    public void errorCodeInAcceptedBodyIsRecordedAndLoggedAsFailure() throws Exception {
        final HttpServer platform = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        platform.createContext("/robot", exchange -> {
            try (InputStream in = exchange.getRequestBody()) {
                in.readAllBytes();
            }
            final byte[] body = "{\"errcode\":130101,\"errmsg\":\"send too fast\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        platform.start();
        try {
            final RecordingCreator creator = new RecordingCreator();
            final HttpAlarmCallback callback = new HttpAlarmCallback(
                HttpAlarmCallback.newHttpClient(Duration.ofSeconds(12)), Duration.ofSeconds(12),
                new AlarmHttpDeliveryMetrics(creator), "dingtalk") {
                @Override
                protected String classifyAcceptedBody(final String body) {
                    return resultOfBodyCode(body, "errcode", Map.of(
                        "130101", AlarmHttpDeliveryMetrics.RESULT_RATE_LIMITED));
                }

                @Override
                protected void doAlarmCallback(final List<AlarmMessage> alarmMessages, final boolean isRecovery) {
                }
            };

            final Logger logger = (Logger) LoggerContext.getContext(false).getLogger(callback.getClass().getName());
            logger.addAppender(APPENDER);
            try {
                callback.post(
                    URI.create("http://127.0.0.1:" + platform.getAddress().getPort() + "/robot"), "{}", Map.of());
            } finally {
                logger.removeAppender(APPENDER);
            }

            assertEquals(List.of("dingtalk,rate_limited"), creator.deliveryKeys);
            assertEquals(List.of("dingtalk,2xx"), creator.statusKeys);
            assertEquals(1, APPENDER.events.size());
            assertEquals(Level.ERROR, APPENDER.events.get(0).getLevel());
        } finally {
            platform.stop(0);
        }
    }

    @Test
    public void resultOfBodyCodeReadsThePlatformCode() {
        final Map<String, String> known = Map.of("310000", AlarmHttpDeliveryMetrics.RESULT_AUTH_FAILED);
        assertEquals(AlarmHttpDeliveryMetrics.RESULT_SUCCESS,
                     HttpAlarmCallback.resultOfBodyCode("{\"errcode\":0,\"errmsg\":\"ok\"}", "errcode", known));
        assertEquals(AlarmHttpDeliveryMetrics.RESULT_SUCCESS,
                     HttpAlarmCallback.resultOfBodyCode("{\"code\":\"0\"}", "code", known));
        assertEquals(AlarmHttpDeliveryMetrics.RESULT_AUTH_FAILED,
                     HttpAlarmCallback.resultOfBodyCode("{\"errcode\":310000}", "errcode", known));
        assertEquals(AlarmHttpDeliveryMetrics.RESULT_REJECTED,
                     HttpAlarmCallback.resultOfBodyCode("{\"errcode\":40008}", "errcode", known));
        // No code, or no JSON object: the 2xx status stands.
        assertEquals(AlarmHttpDeliveryMetrics.RESULT_SUCCESS,
                     HttpAlarmCallback.resultOfBodyCode("{\"status\":\"success\"}", "errcode", known));
        assertEquals(AlarmHttpDeliveryMetrics.RESULT_SUCCESS,
                     HttpAlarmCallback.resultOfBodyCode("ok", "errcode", known));
        assertEquals(AlarmHttpDeliveryMetrics.RESULT_SUCCESS, HttpAlarmCallback.resultOfBodyCode("", "errcode", known));
        assertEquals(AlarmHttpDeliveryMetrics.RESULT_SUCCESS,
                     HttpAlarmCallback.resultOfBodyCode("{\"errcode\":{\"x\":1}}", "errcode", known));
    }

    @Test
    public void interruptedDeliveryIsRecordedWithoutStatusClass() throws Exception {
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch entered = new CountDownLatch(1);
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final HttpServer stalling = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stalling.setExecutor(executor);
        stalling.createContext("/stall-interrupt", exchange -> {
            entered.countDown();
            exchange.sendResponseHeaders(200, 64);
            exchange.getResponseBody().write("ping".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        stalling.start();
        try {
            final RecordingCreator creator = new RecordingCreator();
            final AlarmHttpDeliveryMetrics metrics = new AlarmHttpDeliveryMetrics(creator);
            final Callback callback = new Callback(Duration.ofSeconds(30), metrics, "webhook");
            final URI uri = URI.create(
                "http://127.0.0.1:" + stalling.getAddress().getPort() + "/stall-interrupt");
            final AtomicReference<Throwable> failure = new AtomicReference<>();
            final AtomicBoolean interruptFlagSticky = new AtomicBoolean(true);
            final Thread caller = new Thread(() -> {
                try {
                    callback.send(uri);
                } catch (InterruptedException e) {
                    // Must stay clear: AlarmCore catches Exception/InterruptedException and continues other hooks.
                    interruptFlagSticky.set(Thread.currentThread().isInterrupted());
                    failure.set(e);
                } catch (Throwable thrown) {
                    failure.set(thrown);
                } finally {
                    Thread.interrupted();
                }
            });
            try {
                caller.start();
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                caller.interrupt();
                caller.join(5_000);
                assertInstanceOf(InterruptedException.class, failure.get());
                assertFalse(interruptFlagSticky.get());
                assertEquals(1, creator.deliveryIncrements.get());
                assertEquals(0, creator.statusIncrements.get());
                assertTrue(creator.deliveryKeys.stream().anyMatch(k -> k.contains("interrupted")));
                assertEquals(1, creator.latencies.size());
            } finally {
                caller.interrupt();
                caller.join(5_000);
            }
        } finally {
            release.countDown();
            stalling.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    public void ioErrorIsRecordedWithoutStatusClass() throws Exception {
        final RecordingCreator creator = new RecordingCreator();
        final AlarmHttpDeliveryMetrics metrics = new AlarmHttpDeliveryMetrics(creator);
        final Callback callback = new Callback(Duration.ofSeconds(2), metrics, "webhook");
        // Bind then close so the connect fails deterministically without depending on port 1.
        final ServerSocket closed = new ServerSocket();
        closed.bind(new InetSocketAddress("127.0.0.1", 0));
        final int port = closed.getLocalPort();
        closed.close();
        final URI uri = URI.create("http://127.0.0.1:" + port + "/");
        assertThrows(IOException.class, () -> callback.send(uri));
        assertEquals(1, creator.deliveryIncrements.get());
        assertEquals(0, creator.statusIncrements.get());
        assertTrue(creator.deliveryKeys.stream().anyMatch(k -> k.contains("io_error")));
        assertEquals(1, creator.latencies.size());
    }

    @Test
    public void timeoutIsRecordedWithoutStatusClass() throws Exception {
        final CountDownLatch release = new CountDownLatch(1);
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final HttpServer stalling = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stalling.setExecutor(executor);
        stalling.createContext("/stall-metrics", exchange -> {
            exchange.sendResponseHeaders(200, 64);
            exchange.getResponseBody().write("ping".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        stalling.start();
        try {
            final RecordingCreator creator = new RecordingCreator();
            final AlarmHttpDeliveryMetrics metrics = new AlarmHttpDeliveryMetrics(creator);
            final Callback callback = new Callback(Duration.ofSeconds(1), metrics, "slack");
            final URI uri = URI.create("http://127.0.0.1:" + stalling.getAddress().getPort() + "/stall-metrics");
            assertThrows(HttpTimeoutException.class, () -> callback.send(uri));
            assertEquals(1, creator.deliveryIncrements.get());
            assertEquals(0, creator.statusIncrements.get());
            assertTrue(creator.deliveryKeys.stream().anyMatch(k -> k.contains("timeout")));
            assertEquals(1, creator.latencies.size());
        } finally {
            release.countDown();
            stalling.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    public void stalledResponseBodyTimesOut() throws Exception {
        // The request's own timeout stops at the response headers, so only the delivery deadline bounds the body.
        final CountDownLatch release = new CountDownLatch(1);
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final HttpServer stalling = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stalling.setExecutor(executor);
        stalling.createContext("/stall", exchange -> {
            exchange.sendResponseHeaders(200, 64);
            exchange.getResponseBody().write("ping".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        stalling.start();
        try {
            final URI uri = URI.create("http://127.0.0.1:" + stalling.getAddress().getPort() + "/stall");
            final long started = System.nanoTime();
            assertThrows(HttpTimeoutException.class, () -> new Callback(Duration.ofSeconds(1)).send(uri));
            final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue(elapsedMs < 5_000, "elapsedMs=" + elapsedMs);
        } finally {
            release.countDown();
            stalling.stop(0);
            executor.shutdownNow();
        }
    }
}
