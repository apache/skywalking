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

package org.apache.skywalking.oap.server.library.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(value = 20, unit = TimeUnit.SECONDS)
public class JdkHttpClientUtilsTest {

    @Test
    public void rejectsNonPositiveTimeout() {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1/")).build();
        assertThrows(IllegalArgumentException.class, () -> JdkHttpClientUtils.sendStringWithTimeout(client, request, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> JdkHttpClientUtils.sendStringWithTimeout(client, request, Duration.ofMillis(-1)));
    }

    @Test
    public void extremeTimeoutDoesNotOverflowToNanos() throws Exception {
        try (RawHttp peer = new RawHttp(RawHttp.Mode.CHARSET, 0)) {
            // Larger than Long.MAX_VALUE nanoseconds; the util must not throw ArithmeticException.
            HttpResponse<String> response = send(peer.uri(), Duration.ofSeconds(10_000_000_000L));
            assertEquals("\u00e9", response.body());
        }
    }

    @Test
    public void delayedHeadersTimeOutAndCloseTheConnection() throws Exception {
        try (RawHttp peer = new RawHttp(RawHttp.Mode.DELAY_HEADERS, 5_000)) {
            long started = System.nanoTime();
            // Do not assert the exception message: HttpRequest.timeout and the util share the same
            // deadline, so either "HTTP request timed out" or the JDK's "request timed out" may win.
            assertThrows(HttpTimeoutException.class, () -> send(peer.uri(), Duration.ofSeconds(1)));
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue(elapsedMs >= 700 && elapsedMs < 2_500, "elapsedMs=" + elapsedMs);
            assertTrue(peer.awaitClosed(3, TimeUnit.SECONDS), "stalled connection was not closed");
        }
    }

    @Test
    public void stalledBodyTimesOutAndClosesTheConnection() throws Exception {
        try (RawHttp peer = new RawHttp(RawHttp.Mode.STALL_BODY, 0)) {
            long started = System.nanoTime();
            assertThrows(HttpTimeoutException.class, () -> send(peer.uri(), Duration.ofSeconds(1)));
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue(elapsedMs >= 700 && elapsedMs < 2_500, "elapsedMs=" + elapsedMs);
            assertTrue(peer.awaitClosed(3, TimeUnit.SECONDS), "stalled body connection was not closed");
        }
    }

    @Test
    public void headersAndBodyShareOneDeadline() throws Exception {
        try (RawHttp peer = new RawHttp(RawHttp.Mode.DELAY_THEN_STALL, 600)) {
            long started = System.nanoTime();
            assertThrows(HttpTimeoutException.class, () -> send(peer.uri(), Duration.ofSeconds(1)));
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            // A fresh 1s body budget after the 600ms header delay would finish near 1600ms.
            assertTrue(elapsedMs >= 800 && elapsedMs < 1_500, "elapsedMs=" + elapsedMs);
            assertTrue(peer.awaitClosed(3, TimeUnit.SECONDS));
        }
    }

    @Test
    public void interruptionCancelsTheBodyAndClosesTheConnection() throws Exception {
        try (RawHttp peer = new RawHttp(RawHttp.Mode.STALL_BODY, 0)) {
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread caller = new Thread(() -> {
                try {
                    send(peer.uri(), Duration.ofSeconds(30));
                } catch (Throwable thrown) {
                    failure.set(thrown);
                }
            });
            caller.start();
            assertTrue(peer.awaitAccepted(5, TimeUnit.SECONDS));
            caller.interrupt();
            caller.join(5_000);
            assertInstanceOf(InterruptedException.class, failure.get());
            assertTrue(peer.awaitClosed(3, TimeUnit.SECONDS), "interrupted request left the connection open");
        }
    }

    @Test
    public void cancelBeforeSubscriptionArrivesCancelsTheSubscription() throws Exception {
        Class<?> type = Class.forName(JdkHttpClientUtils.class.getName() + "$CancellableBody");
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object handler = constructor.newInstance();
        Method cancel = type.getDeclaredMethod("cancel", Throwable.class);
        cancel.setAccessible(true);
        cancel.invoke(handler, new HttpTimeoutException("HTTP request timed out"));

        AtomicBoolean cancelled = new AtomicBoolean();
        Flow.Subscription incoming = new Flow.Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
                cancelled.set(true);
            }
        };
        Method onSubscribe = type.getDeclaredMethod("onSubscribe", Flow.Subscription.class);
        onSubscribe.setAccessible(true);
        onSubscribe.invoke(handler, incoming);
        assertTrue(cancelled.get());
    }

    @Test
    public void preservesTheResponseCharset() throws Exception {
        try (RawHttp peer = new RawHttp(RawHttp.Mode.CHARSET, 0)) {
            HttpResponse<String> response = send(peer.uri(), Duration.ofSeconds(2));
            assertEquals("\u00e9", response.body());
        }
    }

    private static HttpResponse<String> send(URI uri, Duration timeout) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build();
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).GET().build();
        return JdkHttpClientUtils.sendStringWithTimeout(client, request, timeout);
    }

    /**
     * Minimal HTTP/1.1 peer. A stalled response keeps the accepted socket open until the client cancels it.
     */
    private static final class RawHttp implements AutoCloseable {
        private enum Mode {
            DELAY_HEADERS, STALL_BODY, DELAY_THEN_STALL, CHARSET
        }

        private final ServerSocket server;

        private final Thread thread;

        private final CountDownLatch accepted = new CountDownLatch(1);

        private final CountDownLatch closed = new CountDownLatch(1);

        private volatile Socket socket;

        private RawHttp(Mode mode, long delayMs) throws IOException {
            server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            thread = new Thread(() -> serve(mode, delayMs), "jdk-http-utils-test");
            thread.setDaemon(true);
            thread.start();
        }

        private URI uri() {
            return URI.create("http://127.0.0.1:" + server.getLocalPort() + "/");
        }

        private boolean awaitAccepted(long time, TimeUnit unit) throws InterruptedException {
            return accepted.await(time, unit);
        }

        private boolean awaitClosed(long time, TimeUnit unit) throws InterruptedException {
            return closed.await(time, unit);
        }

        private void serve(Mode mode, long delayMs) {
            try {
                socket = server.accept();
                socket.setTcpNoDelay(true);
                accepted.countDown();
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                if (!drainRequest(in)) {
                    return;
                }
                if (mode == Mode.DELAY_HEADERS) {
                    if (sleepOrClosed(in, delayMs)) {
                        return;
                    }
                    out.write(ascii("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok"));
                    out.flush();
                    return;
                }
                if (mode == Mode.DELAY_THEN_STALL && sleepOrClosed(in, delayMs)) {
                    return;
                }
                if (mode == Mode.CHARSET) {
                    out.write(ascii("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=ISO-8859-1\r\nContent-Length: 1\r\nConnection: close\r\n\r\n"));
                    out.write(0xE9);
                    out.flush();
                    waitForClose(in);
                    return;
                }
                out.write(ascii("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Length: 64\r\nConnection: keep-alive\r\n\r\n"));
                out.write(ascii("ping"));
                out.flush();
                waitForClose(in);
            } catch (IOException ignored) {
                closed.countDown();
            }
        }

        private boolean sleepOrClosed(InputStream in, long delayMs) throws IOException {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMs);
            socket.setSoTimeout(200);
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
            socket.setSoTimeout(0);
            return false;
        }

        private void waitForClose(InputStream in) throws IOException {
            socket.setSoTimeout(0);
            try {
                if (in.read() < 0) {
                    closed.countDown();
                }
            } catch (IOException e) {
                closed.countDown();
            }
        }

        private boolean drainRequest(InputStream in) throws IOException {
            byte[] end = new byte[] {
                '\r', '\n', '\r', '\n'
            };
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

        private static byte[] ascii(String text) {
            return text.getBytes(StandardCharsets.US_ASCII);
        }

        @Override
        public void close() throws IOException {
            server.close();
            Socket current = socket;
            if (current != null) {
                current.close();
            }
            thread.interrupt();
        }
    }
}
