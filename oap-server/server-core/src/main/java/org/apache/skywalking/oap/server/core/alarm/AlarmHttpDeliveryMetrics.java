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

import io.grpc.Status;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Process-local alarm hook delivery metrics ({@code telemetry.prometheus}).
 * Labels are hook type / result; HTTP hooks also record {@code status_class}.
 */
public final class AlarmHttpDeliveryMetrics {
    public static final String RESULT_SUCCESS = "success";
    /** HTTP 401 / 403, or a platform code for a bad token, key, signature or IP whitelist. */
    public static final String RESULT_AUTH_FAILED = "auth_failed";
    /** HTTP 429, or a platform code for frequency control. */
    public static final String RESULT_RATE_LIMITED = "rate_limited";
    /** Any other non-2xx status below 500, or any other platform error code, such as a keyword miss. */
    public static final String RESULT_REJECTED = "rejected";
    /** HTTP 5xx, or a platform code for a server-side failure. */
    public static final String RESULT_SERVER_ERROR = "server_error";
    /** The WeLink access-token request got no response, or a response that could not be read. */
    public static final String RESULT_TOKEN_ERROR = "token_error";
    public static final String RESULT_TIMEOUT = "timeout";
    public static final String RESULT_IO_ERROR = "io_error";
    public static final String RESULT_INTERRUPTED = "interrupted";

    public static final String STATUS_2XX = "2xx";
    public static final String STATUS_3XX = "3xx";
    public static final String STATUS_4XX = "4xx";
    public static final String STATUS_5XX = "5xx";
    public static final String STATUS_OTHER = "other";

    private final MetricsCreator metricsCreator;
    private final ConcurrentMap<String, CounterMetrics> deliveryTotals = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CounterMetrics> statusTotals = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, HistogramMetrics> latencies = new ConcurrentHashMap<>();

    public AlarmHttpDeliveryMetrics(final MetricsCreator metricsCreator) {
        this.metricsCreator = Objects.requireNonNull(metricsCreator, "metricsCreator");
    }

    /**
     * Record one finished delivery attempt. Never throws.
     *
     * @param statusClass {@code null} when no response was received
     * @param seconds     wall time in seconds
     */
    public void record(final String hookType,
                       final String result,
                       final String statusClass,
                       final double seconds) {
        try {
            final String type = Objects.requireNonNull(hookType, "hookType");
            final String outcome = Objects.requireNonNull(result, "result");
            deliveryTotals.computeIfAbsent(type + '\0' + outcome, ignored -> metricsCreator.createCounter(
                "alarm_http_delivery_total",
                "Alarm hook delivery attempts by hook type and result",
                new MetricsTag.Keys("hook_type", "result"),
                new MetricsTag.Values(type, outcome)
            )).inc();

            if (statusClass != null) {
                statusTotals.computeIfAbsent(type + '\0' + statusClass, ignored -> metricsCreator.createCounter(
                    "alarm_http_delivery_status_total",
                    "HTTP alarm hook responses by hook type and status class",
                    new MetricsTag.Keys("hook_type", "status_class"),
                    new MetricsTag.Values(type, statusClass)
                )).inc();
            }

            latencies.computeIfAbsent(type, ignored -> metricsCreator.createHistogramMetric(
                "alarm_http_delivery_latency_seconds",
                "Alarm hook delivery latency in seconds",
                new MetricsTag.Keys("hook_type"),
                new MetricsTag.Values(type),
                .005, .01, .025, .05, .1, .25, .5, 1, 2.5, 5, 10, 30
            )).observe(Math.max(0, seconds));
        } catch (Throwable ignored) {
            // so11y must not break alarm delivery
        }
    }

    /**
     * The result of a response by its HTTP status alone. A 2xx is {@link #RESULT_SUCCESS} here; platforms that
     * report errors inside a 2xx body classify that body themselves.
     */
    public static String resultOfStatus(final int status) {
        if (status >= 200 && status < 300) {
            return RESULT_SUCCESS;
        }
        if (status == 401 || status == 403) {
            return RESULT_AUTH_FAILED;
        }
        if (status == 429) {
            return RESULT_RATE_LIMITED;
        }
        if (status >= 500 && status < 600) {
            return RESULT_SERVER_ERROR;
        }
        return RESULT_REJECTED;
    }

    public static String statusClassOf(final int status) {
        if (status >= 200 && status < 300) {
            return STATUS_2XX;
        }
        if (status >= 300 && status < 400) {
            return STATUS_3XX;
        }
        if (status >= 400 && status < 500) {
            return STATUS_4XX;
        }
        if (status >= 500 && status < 600) {
            return STATUS_5XX;
        }
        return STATUS_OTHER;
    }

    /**
     * Classify a finished gRPC alarm hook stream. An OAP wait interrupted wins over the stream status
     * (for example {@code DEADLINE_EXCEEDED} arriving after cancellation).
     */
    public static String classifyGrpcDeliveryResult(final boolean interrupted, final Throwable streamError) {
        if (interrupted) {
            return RESULT_INTERRUPTED;
        }
        if (streamError != null) {
            return resultOfGrpcError(streamError);
        }
        return RESULT_SUCCESS;
    }

    /**
     * Map a gRPC stream failure to a delivery {@code result} label:
     * {@code DEADLINE_EXCEEDED} → {@link #RESULT_TIMEOUT};
     * {@code UNAVAILABLE} → {@link #RESULT_IO_ERROR};
     * {@code UNAUTHENTICATED} / {@code PERMISSION_DENIED} → {@link #RESULT_AUTH_FAILED};
     * {@code RESOURCE_EXHAUSTED} → {@link #RESULT_RATE_LIMITED};
     * {@code UNKNOWN} / {@code INTERNAL} / {@code DATA_LOSS} / {@code UNIMPLEMENTED} → {@link #RESULT_SERVER_ERROR};
     * any other code (including peer {@code CANCELLED}) → {@link #RESULT_REJECTED}.
     */
    public static String resultOfGrpcError(final Throwable error) {
        switch (Status.fromThrowable(error).getCode()) {
            case DEADLINE_EXCEEDED:
                return RESULT_TIMEOUT;
            case UNAVAILABLE:
                return RESULT_IO_ERROR;
            case UNAUTHENTICATED:
            case PERMISSION_DENIED:
                return RESULT_AUTH_FAILED;
            case RESOURCE_EXHAUSTED:
                return RESULT_RATE_LIMITED;
            case UNKNOWN:
            case INTERNAL:
            case DATA_LOSS:
            case UNIMPLEMENTED:
                return RESULT_SERVER_ERROR;
            default:
                return RESULT_REJECTED;
        }
    }
}
