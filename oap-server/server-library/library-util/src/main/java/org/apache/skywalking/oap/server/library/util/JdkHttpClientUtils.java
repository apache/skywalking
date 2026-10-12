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

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * JDK {@link HttpClient} helpers.
 * Configuration defaults, status handling, parsing, and retries stay with the caller.
 */
public final class JdkHttpClientUtils {
    private JdkHttpClientUtils() {
    }

    /**
     * Send {@code request} and read the whole response body as a String.
     * {@code timeout} is one budget for the entire operation: time spent waiting for the
     * response headers is subtracted from the time left to read the body.
     * {@link HttpRequest.Builder#timeout(Duration)} does not cover body consumption.
     *
     * <p>{@code request} must carry its own {@link HttpRequest.Builder#timeout(Duration)}, normally the
     * same {@code timeout}: on Java 11 cancelling the pending exchange releases the caller but does not
     * abort a request still waiting for the response headers, only the request's own timeout does.
     */
    public static HttpResponse<String> sendStringWithTimeout(final HttpClient client,
                                                             final HttpRequest request,
                                                             final Duration timeout) throws IOException, InterruptedException {
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (request.timeout().isEmpty()) {
            throw new IllegalArgumentException("request must carry its own timeout");
        }
        final long budgetNanos = budgetNanos(timeout);

        final CancellableBody handler = new CancellableBody();
        final long started = System.nanoTime();
        final CompletableFuture<HttpResponse<String>> pending = client.sendAsync(request, handler);

        try {
            final long remaining = budgetNanos - (System.nanoTime() - started);
            if (remaining <= 0) {
                throw new TimeoutException();
            }
            return pending.get(remaining, TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            final HttpTimeoutException failure = new HttpTimeoutException("HTTP request timed out");
            failure.initCause(e);
            handler.cancel(failure);
            pending.cancel(true);
            throw failure;
        } catch (InterruptedException e) {
            handler.cancel(e);
            pending.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            final Throwable cause = e.getCause();
            handler.cancel(cause);
            pending.cancel(true);
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IOException("HTTP request failed", cause);
        }
    }

    private static long budgetNanos(final Duration positiveTimeout) {
        try {
            return positiveTimeout.toNanos();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * One instance per request. Cancelling the subscriber closes a stalled body read.
     * On Java 11, cancelling only the request future releases the caller but leaves the connection open.
     */
    private static final class CancellableBody implements HttpResponse.BodyHandler<String>, HttpResponse.BodySubscriber<String> {
        private static final Flow.Subscription CANCELLED = new Flow.Subscription() {
            @Override
            public void request(final long n) {
            }

            @Override
            public void cancel() {
            }
        };

        private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();

        private final CompletableFuture<String> body = new CompletableFuture<>();

        private HttpResponse.BodySubscriber<String> delegate;

        @Override
        public HttpResponse.BodySubscriber<String> apply(final HttpResponse.ResponseInfo info) {
            // Preserve the existing response charset handling.
            delegate = HttpResponse.BodyHandlers.ofString().apply(info);
            delegate.getBody().whenComplete((value, error) -> {
                if (error == null) {
                    body.complete(value);
                } else {
                    body.completeExceptionally(error);
                }
            });
            return this;
        }

        @Override
        public CompletableFuture<String> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(final Flow.Subscription incoming) {
            if (subscription.compareAndSet(null, incoming)) {
                delegate.onSubscribe(incoming);
            } else {
                // Includes cancellation before the subscription arrives.
                incoming.cancel();
            }
        }

        @Override
        public void onNext(final List<ByteBuffer> data) {
            delegate.onNext(data);
        }

        @Override
        public void onError(final Throwable error) {
            delegate.onError(error);
        }

        @Override
        public void onComplete() {
            delegate.onComplete();
        }

        void cancel(final Throwable failure) {
            final Flow.Subscription previous = subscription.getAndSet(CANCELLED);
            if (previous != null && previous != CANCELLED) {
                previous.cancel();
            }
            // Cancellation need not trigger onError/onComplete.
            body.completeExceptionally(failure);
        }
    }
}
