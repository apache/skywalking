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

package org.apache.skywalking.oap.server.receiver.otel.otlp;

import com.google.protobuf.ByteString;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.MediaType;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.logs.v1.ResourceLogs;
import io.opentelemetry.proto.logs.v1.ScopeLogs;
import io.opentelemetry.proto.resource.v1.Resource;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.log.analyzer.v2.module.LogAnalyzerModule;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.ILogAnalyzerService;
import org.apache.skywalking.oap.server.core.source.LogMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.receiver.otel.OtelMetricReceiverConfig;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * An OTLP log record's own trace id and span id reach the log analysis, so the stored log is found
 * from its trace and from its span.
 */
class OpenTelemetryLogHandlerTraceContextTest {
    private static final byte[] TRACE_ID = {
        0x0a, 0x1b, 0x2c, 0x3d, 0x4e, 0x5f, 0x60, 0x71, (byte) 0x82, (byte) 0x93, (byte) 0xa4, (byte) 0xb5,
        (byte) 0xc6, (byte) 0xd7, (byte) 0xe8, (byte) 0xf9
    };
    private static final byte[] SPAN_ID = {0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xab, (byte) 0xcd, (byte) 0xef};

    private final List<LogMetadata> metadata = new ArrayList<>();
    private final List<LogData.Builder> inputs = new ArrayList<>();

    @Test
    void aRecordInATraceCarriesItsTraceIdAndSpanId() {
        export(LogRecord.newBuilder()
                        .setTraceId(ByteString.copyFrom(TRACE_ID))
                        .setSpanId(ByteString.copyFrom(SPAN_ID)));

        final LogMetadata.TraceContext context = metadata.get(0).getTraceContext();
        assertEquals("0a1b2c3d4e5f60718293a4b5c6d7e8f9", context.getTraceId());
        assertEquals("0123456789abcdef", context.getOtlpSpanId());
        assertEquals("", context.getTraceSegmentId(), "an OTLP span is not a native segment");
        assertEquals("0a1b2c3d4e5f60718293a4b5c6d7e8f9", inputs.get(0).getTraceContext().getTraceId(),
                     "LAL reads the trace id off the LogData too");
    }

    @Test
    void aRecordOutsideATraceCarriesNeither() {
        export(LogRecord.newBuilder());
        export(LogRecord.newBuilder()
                        .setTraceId(ByteString.copyFrom(new byte[16]))
                        .setSpanId(ByteString.copyFrom(new byte[8])));

        for (final LogMetadata m : metadata) {
            assertEquals("", m.getTraceContext().getTraceId(), "an absent or all-zero trace id is no trace id");
            assertEquals("", m.getTraceContext().getOtlpSpanId(), "an absent or all-zero span id is no span id");
        }
    }

    @Test
    void aSpanIdWithoutATraceIdLeavesTheSharedEmptyContextAlone() {
        export(LogRecord.newBuilder().setSpanId(ByteString.copyFrom(SPAN_ID)));
        export(LogRecord.newBuilder());

        assertEquals("0123456789abcdef", metadata.get(0).getTraceContext().getOtlpSpanId());
        assertEquals("", metadata.get(1).getTraceContext().getOtlpSpanId(),
                     "the next record without ids must not inherit the previous one's span id");
    }

    @Test
    void anOtlpJsonRecordKeepsTheIdsItsSenderWrote() {
        final OpenTelemetryLogHandler handler = new OpenTelemetryLogHandler();
        handler.init(manager(), new OtelMetricReceiverConfig());
        new OpenTelemetryLogHTTPHandler(handler).collectJson(AggregatedHttpRequest.of(
            HttpMethod.POST, "/v1/logs", MediaType.JSON_UTF_8,
            "{\"resourceLogs\":[{\"resource\":{\"attributes\":[{\"key\":\"service.name\","
                + "\"value\":{\"stringValue\":\"checkout\"}}]},\"scopeLogs\":[{\"logRecords\":[{"
                + "\"timeUnixNano\":\"1700000000000000000\",\"body\":{\"stringValue\":\"a line\"},"
                + "\"traceId\":\"0a1b2c3d4e5f60718293a4b5c6d7e8f9\",\"spanId\":\"0123456789abcdef\"}]}]}]}"));

        assertEquals("0a1b2c3d4e5f60718293a4b5c6d7e8f9", metadata.get(0).getTraceContext().getTraceId(),
                     "OTLP/JSON writes ids as hex, not as the base64 protobuf's JSON parser expects");
        assertEquals("0123456789abcdef", metadata.get(0).getTraceContext().getOtlpSpanId());
    }

    @Test
    void aTraceIdWithAZeroUpperHalfIsWrittenAsTheTraceStoreWritesIt() {
        final byte[] sixtyFourBit = {0, 0, 0, 0, 0, 0, 0, 0, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
        export(LogRecord.newBuilder().setTraceId(ByteString.copyFrom(sixtyFourBit)), OtelMetricReceiverConfig.OTLP_TRACE_STORAGE_OTLP);
        export(LogRecord.newBuilder().setTraceId(ByteString.copyFrom(sixtyFourBit)), OtelMetricReceiverConfig.OTLP_TRACE_STORAGE_ZIPKIN);

        assertEquals("00000000000000000102030405060708", metadata.get(0).getTraceContext().getTraceId(),
                     "the native OTLP span store keeps all 32 characters");
        assertEquals("0102030405060708", metadata.get(1).getTraceContext().getTraceId(),
                     "Zipkin keeps the lower 64 bits of an ID whose upper 64 are zero");
    }

    private void export(final LogRecord.Builder record) {
        export(record, OtelMetricReceiverConfig.OTLP_TRACE_STORAGE_OTLP);
    }

    private void export(final LogRecord.Builder record, final String traceStorage) {
        final OpenTelemetryLogHandler handler = new OpenTelemetryLogHandler();
        final OtelMetricReceiverConfig config = new OtelMetricReceiverConfig();
        config.setOtlpTraceStorage(traceStorage);
        handler.init(manager(), config);
        handler.processExport(ExportLogsServiceRequest.newBuilder().addResourceLogs(
            ResourceLogs.newBuilder()
                        .setResource(Resource.newBuilder().addAttributes(
                            KeyValue.newBuilder().setKey("service.name")
                                    .setValue(AnyValue.newBuilder().setStringValue("checkout"))))
                        .addScopeLogs(ScopeLogs.newBuilder().addLogRecords(
                            record.setTimeUnixNano(1_700_000_000_000_000_000L)
                                  .setBody(AnyValue.newBuilder().setStringValue("a line")))))
                                                        .build());
    }

    private ModuleManager manager() {
        final ILogAnalyzerService analyzer = mock(ILogAnalyzerService.class);
        doAnswer(inv -> {
            metadata.add(inv.getArgument(0));
            inputs.add(inv.getArgument(1));
            return null;
        }).when(analyzer).doAnalysis(any(), any());
        final MetricsCreator metricsCreator = mock(MetricsCreator.class);
        when(metricsCreator.createHistogramMetric(anyString(), anyString(), any(), any()))
            .thenReturn(new HistogramMetrics() {
                @Override
                public void observe(final double value) {
                }
            });
        final ModuleManager manager = mock(ModuleManager.class);
        provide(manager, LogAnalyzerModule.NAME, ILogAnalyzerService.class, analyzer);
        provide(manager, TelemetryModule.NAME, MetricsCreator.class, metricsCreator);
        return manager;
    }

    private static <T extends Service> void provide(final ModuleManager manager, final String module,
                                                    final Class<T> type, final T service) {
        final ModuleServiceHolder services = mock(ModuleServiceHolder.class);
        when(services.getService(type)).thenReturn(service);
        final ModuleProviderHolder provider = mock(ModuleProviderHolder.class);
        when(provider.provider()).thenReturn(services);
        when(manager.find(module)).thenReturn(provider);
    }
}
