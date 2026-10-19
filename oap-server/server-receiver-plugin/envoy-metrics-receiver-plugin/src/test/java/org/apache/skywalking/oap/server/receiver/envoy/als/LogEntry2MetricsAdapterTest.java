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

package org.apache.skywalking.oap.server.receiver.envoy.als;

import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import io.envoyproxy.envoy.data.accesslog.v3.AccessLogCommon;
import io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry;
import io.envoyproxy.envoy.data.accesslog.v3.HTTPResponseProperties;
import io.envoyproxy.envoy.data.accesslog.v3.TCPAccessLogEntry;
import org.apache.skywalking.apm.network.servicemesh.v3.HTTPServiceMeshMetric;
import org.apache.skywalking.apm.network.servicemesh.v3.TCPServiceMeshMetric;
import org.apache.skywalking.oap.server.receiver.envoy.als.tcp.TCPLogEntry2MetricsAdapter;
import org.junit.jupiter.api.Test;

import static org.apache.skywalking.oap.server.receiver.envoy.als.LogEntry2MetricsAdapter.UNKNOWN_INTERNAL_LATENCY;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LogEntry2MetricsAdapterTest {
    // 1.5s to send upstream, first response byte at 2.9s, first and last byte sent back at 3.1s.
    private static final AccessLogCommon COMMON = AccessLogCommon.newBuilder()
        .setStartTime(Timestamp.newBuilder().setSeconds(1700000000))
        .setTimeToFirstUpstreamTxByte(Duration.newBuilder().setSeconds(1).setNanos(500_000_000))
        .setTimeToLastUpstreamRxByte(Duration.newBuilder().setSeconds(3))
        .setTimeToFirstUpstreamRxByte(Duration.newBuilder().setSeconds(2).setNanos(900_000_000))
        .setTimeToFirstDownstreamTxByte(Duration.newBuilder().setSeconds(3).setNanos(100_000_000))
        .setTimeToLastDownstreamTxByte(Duration.newBuilder().setSeconds(3).setNanos(100_000_000))
        .build();

    // An upstream timeout: the request went upstream at 2ms, no response came back, and Envoy sent its own 504 at 15s.
    private static final AccessLogCommon UPSTREAM_TIMEOUT = AccessLogCommon.newBuilder()
        .setStartTime(Timestamp.newBuilder().setSeconds(1700000000))
        .setTimeToFirstUpstreamTxByte(Duration.newBuilder().setNanos(2_000_000))
        .setTimeToFirstDownstreamTxByte(Duration.newBuilder().setSeconds(15).setNanos(2_000_000))
        .setTimeToLastDownstreamTxByte(Duration.newBuilder().setSeconds(15).setNanos(2_000_000))
        .build();

    // The upstream answered at 2.9s and nothing has been sent back yet, as in the log Envoy flushes once an HTTP tunnel
    // is established, and in TCP proxy logs, which carry no downstream byte timings.
    private static final AccessLogCommon NO_DOWNSTREAM_TX = AccessLogCommon.newBuilder()
        .setStartTime(Timestamp.newBuilder().setSeconds(1700000000))
        .setTimeToFirstUpstreamTxByte(Duration.newBuilder().setSeconds(1).setNanos(500_000_000))
        .setTimeToFirstUpstreamRxByte(Duration.newBuilder().setSeconds(2).setNanos(900_000_000))
        .build();

    // A retry: the first attempt got a 503 after 3s, and the second went upstream at 3.029s, got its first response
    // byte at 3.5s, and the first byte was sent back at 3.6s.
    private static final AccessLogCommon RETRIED = AccessLogCommon.newBuilder()
        .setStartTime(Timestamp.newBuilder().setSeconds(1700000000))
        .setUpstreamRequestAttemptCount(2)
        .setTimeToFirstUpstreamTxByte(Duration.newBuilder().setSeconds(3).setNanos(29_000_000))
        .setTimeToFirstUpstreamRxByte(Duration.newBuilder().setSeconds(3).setNanos(500_000_000))
        .setTimeToFirstDownstreamTxByte(Duration.newBuilder().setSeconds(3).setNanos(600_000_000))
        .setTimeToLastDownstreamTxByte(Duration.newBuilder().setSeconds(3).setNanos(600_000_000))
        .build();

    // A TCP proxy tunneling over HTTP CONNECT that connected on its second attempt. The TCP proxy logs no downstream
    // byte timings.
    private static final AccessLogCommon RETRIED_CONNECT = AccessLogCommon.newBuilder()
        .setStartTime(Timestamp.newBuilder().setSeconds(1700000000))
        .setUpstreamRequestAttemptCount(2)
        .setTimeToFirstUpstreamTxByte(Duration.newBuilder().setSeconds(3).setNanos(29_000_000))
        .setTimeToFirstUpstreamRxByte(Duration.newBuilder().setSeconds(3).setNanos(500_000_000))
        .build();

    // The global timeout fired during the backoff after the first attempt's 503 at 3s, before the retry started, so the
    // entry keeps that attempt's timings and the count of 1, and Envoy sent its own 504 at 3.5s.
    private static final AccessLogCommon TIMEOUT_IN_RETRY_BACKOFF = AccessLogCommon.newBuilder()
        .setStartTime(Timestamp.newBuilder().setSeconds(1700000000))
        .setUpstreamRequestAttemptCount(1)
        .setTimeToFirstUpstreamTxByte(Duration.newBuilder().setNanos(2_000_000))
        .setTimeToFirstUpstreamRxByte(Duration.newBuilder().setSeconds(3))
        .setTimeToFirstDownstreamTxByte(Duration.newBuilder().setSeconds(3).setNanos(500_000_000))
        .setTimeToLastDownstreamTxByte(Duration.newBuilder().setSeconds(3).setNanos(500_000_000))
        .build();

    // A filter answered with its own reply at 1ms, so nothing went upstream.
    private static final AccessLogCommon NO_UPSTREAM = AccessLogCommon.newBuilder()
        .setStartTime(Timestamp.newBuilder().setSeconds(1700000000))
        .setTimeToFirstDownstreamTxByte(Duration.newBuilder().setNanos(1_000_000))
        .setTimeToLastDownstreamTxByte(Duration.newBuilder().setNanos(1_000_000))
        .build();

    @Test
    public void testHTTPInternalLatencyCountsWholeSeconds() {
        final HTTPAccessLogEntry entry = HTTPAccessLogEntry.newBuilder().setCommonProperties(COMMON).build();
        final HTTPServiceMeshMetric.Builder metrics =
            new LogEntry2MetricsAdapter(entry, null, null).adaptToDownstreamMetrics();

        assertEquals(1_500_000_000L, metrics.getInternalRequestLatencyNanos());
        assertEquals(200_000_000L, metrics.getInternalResponseLatencyNanos());
    }

    @Test
    public void testHTTPInternalResponseLatencyWithoutUpstreamResponse() {
        final HTTPAccessLogEntry entry = HTTPAccessLogEntry.newBuilder().setCommonProperties(UPSTREAM_TIMEOUT).build();
        final HTTPServiceMeshMetric.Builder metrics =
            new LogEntry2MetricsAdapter(entry, null, null).adaptToDownstreamMetrics();

        assertEquals(2_000_000L, metrics.getInternalRequestLatencyNanos());
        assertEquals(UNKNOWN_INTERNAL_LATENCY, metrics.getInternalResponseLatencyNanos());
    }

    @Test
    public void testHTTPInternalResponseLatencyWithoutDownstreamResponse() {
        final HTTPAccessLogEntry entry = HTTPAccessLogEntry.newBuilder().setCommonProperties(NO_DOWNSTREAM_TX).build();
        final HTTPServiceMeshMetric.Builder metrics =
            new LogEntry2MetricsAdapter(entry, null, null).adaptToDownstreamMetrics();

        assertEquals(1_500_000_000L, metrics.getInternalRequestLatencyNanos());
        assertEquals(UNKNOWN_INTERNAL_LATENCY, metrics.getInternalResponseLatencyNanos());
    }

    @Test
    public void testHTTPInternalLatencyWithOneAttempt() {
        final AccessLogCommon oneAttempt = COMMON.toBuilder().setUpstreamRequestAttemptCount(1).build();
        final HTTPAccessLogEntry entry = HTTPAccessLogEntry.newBuilder().setCommonProperties(oneAttempt).build();
        final HTTPServiceMeshMetric.Builder metrics =
            new LogEntry2MetricsAdapter(entry, null, null).adaptToDownstreamMetrics();

        assertEquals(1_500_000_000L, metrics.getInternalRequestLatencyNanos());
        assertEquals(200_000_000L, metrics.getInternalResponseLatencyNanos());
    }

    @Test
    public void testHTTPInternalRequestLatencyAfterRetry() {
        final HTTPAccessLogEntry entry = HTTPAccessLogEntry.newBuilder().setCommonProperties(RETRIED).build();
        final HTTPServiceMeshMetric.Builder metrics =
            new LogEntry2MetricsAdapter(entry, null, null).adaptToDownstreamMetrics();

        assertEquals(UNKNOWN_INTERNAL_LATENCY, metrics.getInternalRequestLatencyNanos());
        assertEquals(100_000_000L, metrics.getInternalResponseLatencyNanos());
    }

    @Test
    public void testHTTPInternalResponseLatencyViaUpstream() {
        final HTTPAccessLogEntry entry = HTTPAccessLogEntry.newBuilder()
            .setCommonProperties(COMMON)
            .setResponse(HTTPResponseProperties.newBuilder().setResponseCodeDetails("via_upstream"))
            .build();
        final HTTPServiceMeshMetric.Builder metrics =
            new LogEntry2MetricsAdapter(entry, null, null).adaptToDownstreamMetrics();

        assertEquals(200_000_000L, metrics.getInternalResponseLatencyNanos());
    }

    @Test
    public void testHTTPInternalResponseLatencyOfEnvoyReply() {
        final HTTPAccessLogEntry entry = HTTPAccessLogEntry.newBuilder()
            .setCommonProperties(TIMEOUT_IN_RETRY_BACKOFF)
            .setResponse(HTTPResponseProperties.newBuilder().setResponseCodeDetails("response_timeout"))
            .build();
        final HTTPServiceMeshMetric.Builder metrics =
            new LogEntry2MetricsAdapter(entry, null, null).adaptToDownstreamMetrics();

        assertEquals(2_000_000L, metrics.getInternalRequestLatencyNanos());
        assertEquals(UNKNOWN_INTERNAL_LATENCY, metrics.getInternalResponseLatencyNanos());
    }

    @Test
    public void testHTTPInternalLatencyWithoutUpstreamRequest() {
        final HTTPAccessLogEntry entry = HTTPAccessLogEntry.newBuilder().setCommonProperties(NO_UPSTREAM).build();
        final HTTPServiceMeshMetric.Builder metrics =
            new LogEntry2MetricsAdapter(entry, null, null).adaptToDownstreamMetrics();

        assertEquals(UNKNOWN_INTERNAL_LATENCY, metrics.getInternalRequestLatencyNanos());
        assertEquals(UNKNOWN_INTERNAL_LATENCY, metrics.getInternalResponseLatencyNanos());
    }

    @Test
    public void testTCPInternalLatencyCountsWholeSeconds() {
        final TCPAccessLogEntry entry = TCPAccessLogEntry.newBuilder().setCommonProperties(COMMON).build();
        final TCPServiceMeshMetric.Builder metrics =
            new TCPLogEntry2MetricsAdapter(entry, null, null).adaptToDownstreamMetrics();

        assertEquals(1_500_000_000L, metrics.getInternalRequestLatencyNanos());
        assertEquals(200_000_000L, metrics.getInternalResponseLatencyNanos());
    }

    @Test
    public void testTCPInternalResponseLatencyWithoutDownstreamResponse() {
        final TCPAccessLogEntry entry = TCPAccessLogEntry.newBuilder().setCommonProperties(NO_DOWNSTREAM_TX).build();
        final TCPServiceMeshMetric.Builder metrics =
            new TCPLogEntry2MetricsAdapter(entry, null, null).adaptToDownstreamMetrics();

        assertEquals(1_500_000_000L, metrics.getInternalRequestLatencyNanos());
        assertEquals(UNKNOWN_INTERNAL_LATENCY, metrics.getInternalResponseLatencyNanos());
    }

    @Test
    public void testTCPInternalRequestLatencyAfterRetriedConnect() {
        final TCPAccessLogEntry entry = TCPAccessLogEntry.newBuilder().setCommonProperties(RETRIED_CONNECT).build();
        final TCPServiceMeshMetric.Builder metrics =
            new TCPLogEntry2MetricsAdapter(entry, null, null).adaptToDownstreamMetrics();

        assertEquals(UNKNOWN_INTERNAL_LATENCY, metrics.getInternalRequestLatencyNanos());
        assertEquals(UNKNOWN_INTERNAL_LATENCY, metrics.getInternalResponseLatencyNanos());
    }

    @Test
    public void testTCPInternalLatencyWithoutUpstreamTimings() {
        final TCPAccessLogEntry entry = TCPAccessLogEntry.newBuilder().setCommonProperties(NO_UPSTREAM).build();
        final TCPServiceMeshMetric.Builder metrics =
            new TCPLogEntry2MetricsAdapter(entry, null, null).adaptToDownstreamMetrics();

        assertEquals(UNKNOWN_INTERNAL_LATENCY, metrics.getInternalRequestLatencyNanos());
        assertEquals(UNKNOWN_INTERNAL_LATENCY, metrics.getInternalResponseLatencyNanos());
    }
}
