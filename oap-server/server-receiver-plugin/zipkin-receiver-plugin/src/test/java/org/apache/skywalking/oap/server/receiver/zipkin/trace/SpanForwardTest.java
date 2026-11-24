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

package org.apache.skywalking.oap.server.receiver.zipkin.trace;

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.trace.SpanListenerManager;
import org.apache.skywalking.oap.server.core.trace.SpanListenerResult;
import org.apache.skywalking.oap.server.core.trace.TraceSpansDropped;
import org.apache.skywalking.oap.server.core.zipkin.source.ZipkinSpan;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.receiver.zipkin.ZipkinReceiverConfig;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zipkin2.Endpoint;
import zipkin2.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Zipkin receiver counts each span it does not store under its reason, and one span that fails costs only
 * that span.
 */
class SpanForwardTest {
    /**
     * Low 64 bits of 1, which every sample rate keeps.
     */
    private static final String KEPT_TRACE_ID = "00000000000000000000000000000001";
    /**
     * Low 64 bits near {@code Long.MAX_VALUE}, which a 50% sample rate leaves out.
     */
    private static final String SAMPLED_OUT_TRACE_ID = "00000000000000007fffffffffffffff";

    private final List<String> received = new ArrayList<>();
    private final TraceSpansDropped droppedSpans = mock(TraceSpansDropped.class);
    private final HistogramMetrics latency = mock(HistogramMetrics.class);
    private final ZipkinReceiverConfig config = new ZipkinReceiverConfig();
    private NamingControl namingControl;
    private ModuleManager manager;

    @BeforeEach
    void setUp() {
        namingControl = mock(NamingControl.class);
        when(namingControl.formatServiceName(any())).thenAnswer(inv -> inv.getArgument(0));
        when(namingControl.formatEndpointName(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        final SourceReceiver receiver = mock(SourceReceiver.class);
        doAnswer(inv -> {
            if (inv.getArgument(0) instanceof ZipkinSpan) {
                received.add(((ZipkinSpan) inv.getArgument(0)).getName());
            }
            return null;
        }).when(receiver).receive(any());
        final SpanListenerManager listeners = mock(SpanListenerManager.class);
        when(listeners.notifyZipkinPhase(any())).thenReturn(SpanListenerResult.CONTINUE);

        final ModuleServiceHolder services = mock(ModuleServiceHolder.class);
        when(services.getService(NamingControl.class)).thenReturn(namingControl);
        when(services.getService(SourceReceiver.class)).thenReturn(receiver);
        when(services.getService(SpanListenerManager.class)).thenReturn(listeners);
        final ModuleProviderHolder provider = mock(ModuleProviderHolder.class);
        when(provider.provider()).thenReturn(services);
        manager = mock(ModuleManager.class);
        when(manager.find(CoreModule.NAME)).thenReturn(provider);
    }

    @Test
    void aSampledOutSpanIsCountedAsSampled() {
        config.setSampleRate(5000);

        new SpanForward(config, manager).send(
            List.of(span("kept", KEPT_TRACE_ID), span("left-out", SAMPLED_OUT_TRACE_ID)), latency, droppedSpans);

        assertEquals(List.of("kept"), received);
        verify(droppedSpans).count(TraceSpansDropped.Reason.SAMPLED, 1);
        verify(droppedSpans, never()).count(eq(TraceSpansDropped.Reason.RATE_LIMITED), anyLong());
    }

    @Test
    void aSpanOverTheLimitIsCountedAsRateLimited() {
        config.setMaxSpansPerSecond(1);

        new SpanForward(config, manager).send(
            List.of(span("first", KEPT_TRACE_ID), span("second", KEPT_TRACE_ID)), latency, droppedSpans);

        assertEquals(List.of("first"), received);
        verify(droppedSpans).count(TraceSpansDropped.Reason.RATE_LIMITED, 1);
    }

    @Test
    void aDebugSpanSkipsTheSamplingAndTheLimit() {
        config.setSampleRate(5000);
        config.setMaxSpansPerSecond(1);

        new SpanForward(config, manager).send(
            List.of(debug("a"), debug("b"), debug("c")), latency, droppedSpans);

        assertEquals(List.of("a", "b", "c"), received);
        verify(droppedSpans, never()).count(any(), anyLong());
    }

    @Test
    void aSpanThatFailsIsDroppedAloneAndTheRestAreProcessed() {
        when(namingControl.formatEndpointName(anyString(), eq("bad")))
            .thenThrow(new IllegalStateException("broken name"));

        new SpanForward(config, manager).send(
            List.of(span("before", KEPT_TRACE_ID), span("bad", KEPT_TRACE_ID), span("after", KEPT_TRACE_ID)),
            latency, droppedSpans);

        assertEquals(List.of("before", "after"), received);
        verify(droppedSpans).count(TraceSpansDropped.Reason.PROCESS_FAILED, 1);
        verify(latency, times(3)).observe(anyDouble());
    }

    private static Span span(final String name, final String traceId) {
        return builder(name, traceId).build();
    }

    private static Span debug(final String name) {
        return builder(name, SAMPLED_OUT_TRACE_ID).debug(true).build();
    }

    private static Span.Builder builder(final String name, final String traceId) {
        return Span.newBuilder()
                   .traceId(traceId)
                   .id(String.format("%016x", name.hashCode() & 0xffffffffL | 1L))
                   .name(name)
                   .kind(Span.Kind.SERVER)
                   .localEndpoint(Endpoint.newBuilder().serviceName("cart").build())
                   .timestamp(1_700_000_000_000_000L)
                   .duration(100L);
    }
}
