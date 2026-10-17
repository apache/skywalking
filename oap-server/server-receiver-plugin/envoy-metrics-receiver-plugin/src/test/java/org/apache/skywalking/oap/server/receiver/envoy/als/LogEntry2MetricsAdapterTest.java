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
import io.envoyproxy.envoy.data.accesslog.v3.TCPAccessLogEntry;
import org.apache.skywalking.apm.network.servicemesh.v3.HTTPServiceMeshMetric;
import org.apache.skywalking.apm.network.servicemesh.v3.TCPServiceMeshMetric;
import org.apache.skywalking.oap.server.receiver.envoy.als.tcp.TCPLogEntry2MetricsAdapter;
import org.junit.jupiter.api.Test;

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
        assertEquals(0L, metrics.getInternalResponseLatencyNanos());
    }

    @Test
    public void testHTTPInternalResponseLatencyWithoutDownstreamResponse() {
        final HTTPAccessLogEntry entry = HTTPAccessLogEntry.newBuilder().setCommonProperties(NO_DOWNSTREAM_TX).build();
        final HTTPServiceMeshMetric.Builder metrics =
            new LogEntry2MetricsAdapter(entry, null, null).adaptToDownstreamMetrics();

        assertEquals(1_500_000_000L, metrics.getInternalRequestLatencyNanos());
        assertEquals(0L, metrics.getInternalResponseLatencyNanos());
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
        assertEquals(0L, metrics.getInternalResponseLatencyNanos());
    }
}
