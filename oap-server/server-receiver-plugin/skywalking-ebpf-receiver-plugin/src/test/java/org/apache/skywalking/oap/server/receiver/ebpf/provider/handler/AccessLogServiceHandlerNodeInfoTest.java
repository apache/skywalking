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

package org.apache.skywalking.oap.server.receiver.ebpf.provider.handler;

import org.apache.skywalking.apm.network.common.v3.Instant;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.EBPFAccessLogNodeInfo;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.EBPFOffsetTimestamp;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.EBPFTimestamp;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class AccessLogServiceHandlerNodeInfoTest {

    @Test
    public void parseTimestampKeepsSubSecondOffset() {
        // boot at epoch 1000s; offset 1.5s → 1001500 ms (old code truncated to 1001000)
        final AccessLogServiceHandler.NodeInfo node = nodeInfo(1000L, 0);
        final long millis = node.parseTimestamp(offset(1_500_000_000L));
        assertEquals(1_001_500L, millis);
    }

    @Test
    public void parseTimestampIncludesBootTimeNanos() {
        // boot at 1000s + 500ms; offset 200ms → 1000700 ms
        final AccessLogServiceHandler.NodeInfo node = nodeInfo(1000L, 500_000_000);
        final long millis = node.parseTimestamp(offset(200_000_000L));
        assertEquals(1_000_700L, millis);
    }

    @Test
    public void parseTimestampCarriesBootNanosAcrossSecond() {
        // boot 1000s + 999ms; offset 2ms → 1001001 ms
        final AccessLogServiceHandler.NodeInfo node = nodeInfo(1000L, 999_000_000);
        assertEquals(1_001_001L, node.parseTimestamp(offset(2_000_000L)));
    }

    @Test
    public void parseTimestampWholeSecondOffsetMatchesLegacyWhenBootNanosZero() {
        // regression: exact whole-second offsets with zero boot nanos must match the old formula
        final long bootSeconds = 1_700_000_000L;
        final long offsetNanos = 42L * 1_000_000_000L;
        final AccessLogServiceHandler.NodeInfo node = nodeInfo(bootSeconds, 0);
        final long legacy = TimeUnit.SECONDS.toMillis(bootSeconds + TimeUnit.NANOSECONDS.toSeconds(offsetNanos));
        assertEquals(legacy, node.parseTimestamp(offset(offsetNanos)));
    }

    @Test
    public void parseTimestampFloorsSubMillisecondRemainder() {
        // toEpochMilli drops sub-ms nanos, same as Envoy ALS formatAsLong
        final AccessLogServiceHandler.NodeInfo node = nodeInfo(1000L, 0);
        assertEquals(1_000_000L, node.parseTimestamp(offset(999_999L)));
        assertEquals(1_000_001L, node.parseTimestamp(offset(1_000_000L)));
    }

    @Test
    public void parseTimestampMeshLatencyUsesMillisDifference() {
        final AccessLogServiceHandler.NodeInfo node = nodeInfo(1_700_000_000L, 0);
        final long start = node.parseTimestamp(offset(10_000_000_000L));
        final long end = node.parseTimestamp(offset(10_123_000_000L));
        assertEquals(123L, end - start);
    }

    @Test
    public void parseMinuteTimeBucketUsesSameEpochMillis() {
        final AccessLogServiceHandler.NodeInfo node = nodeInfo(1000L, 0);
        final EBPFTimestamp ts = offset(1_500_000_000L);
        assertEquals(TimeBucket.getMinuteTimeBucket(1_001_500L), node.parseMinuteTimeBucket(ts));
    }

    @Test
    public void parseMinuteTimeBucketDiffersFromSecondTruncationNearBoundary() {
        // Align boot to a minute boundary so fractional seconds can cross into the next minute.
        final long bootSeconds = 1_699_999_980L; // 1700000000 - 20; % 60 == 0
        final AccessLogServiceHandler.NodeInfo node = nodeInfo(bootSeconds, 300_000_000);
        // correct: 0.3s + 59.8s = 60.1s past boot → next minute
        // legacy: floor(59.8s) = 59s, ignores boot nanos → still previous minute
        final long offsetNanos = 59_800_000_000L;
        final long correctBucket = node.parseMinuteTimeBucket(offset(offsetNanos));
        final long legacySeconds = bootSeconds + TimeUnit.NANOSECONDS.toSeconds(offsetNanos);
        final long legacyBucket = TimeBucket.getMinuteTimeBucket(legacySeconds * 1000);
        assertNotEquals(legacyBucket, correctBucket);
        assertEquals(TimeBucket.getMinuteTimeBucket(node.parseTimestamp(offset(offsetNanos))), correctBucket);
    }

    private static AccessLogServiceHandler.NodeInfo nodeInfo(long seconds, int nanos) {
        return new AccessLogServiceHandler.NodeInfo(EBPFAccessLogNodeInfo.newBuilder()
            .setName("node-1")
            .setBootTime(Instant.newBuilder().setSeconds(seconds).setNanos(nanos).build())
            .build());
    }

    private static EBPFTimestamp offset(long nanosSinceBoot) {
        return EBPFTimestamp.newBuilder()
            .setOffset(EBPFOffsetTimestamp.newBuilder().setOffset(nanosSinceBoot).build())
            .build();
    }
}
