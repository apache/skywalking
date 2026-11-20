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

import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.logs.v1.ResourceLogs;
import io.opentelemetry.proto.logs.v1.ScopeLogs;
import io.opentelemetry.proto.logs.v1.SeverityNumber;
import io.opentelemetry.proto.resource.v1.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.log.analyzer.v2.module.LogAnalyzerModule;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.ILogAnalyzerService;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The tags an OTLP log record reaches the log analysis with, its attributes and its severity number as the
 * {@code severity} and {@code level} tags, and the searchable tag keys the log is indexed by.
 */
class OpenTelemetryLogHandlerTagsTest {
    private final List<LogMetadata> metadata = new ArrayList<>();
    private final List<LogData.Builder> inputs = new ArrayList<>();

    @Test
    void theSeverityNumberIsTaggedByItsEnumName() {
        export(LogRecord.newBuilder()
                        .setSeverityNumber(SeverityNumber.SEVERITY_NUMBER_ERROR)
                        .setSeverityText("error"));
        export(LogRecord.newBuilder()
                        .setSeverityNumber(SeverityNumber.SEVERITY_NUMBER_INFO2)
                        .setSeverityText("Notice"));

        assertEquals("SEVERITY_NUMBER_ERROR", tagsOf(0).get("severity"), "the number decides, not the text");
        assertEquals("SEVERITY_NUMBER_INFO2", tagsOf(1).get("severity"), "a finer level keeps its own name");
    }

    @Test
    void theLevelIsTheNameOfTheSeverityRange() {
        export(LogRecord.newBuilder().setSeverityNumber(SeverityNumber.SEVERITY_NUMBER_TRACE));
        export(LogRecord.newBuilder().setSeverityNumber(SeverityNumber.SEVERITY_NUMBER_DEBUG4));
        export(LogRecord.newBuilder().setSeverityNumber(SeverityNumber.SEVERITY_NUMBER_INFO).setSeverityText("Information"));
        export(LogRecord.newBuilder().setSeverityNumber(SeverityNumber.SEVERITY_NUMBER_INFO2).setSeverityText("Notice"));
        export(LogRecord.newBuilder().setSeverityNumber(SeverityNumber.SEVERITY_NUMBER_WARN3));
        export(LogRecord.newBuilder().setSeverityNumber(SeverityNumber.SEVERITY_NUMBER_ERROR2));
        export(LogRecord.newBuilder().setSeverityNumber(SeverityNumber.SEVERITY_NUMBER_FATAL4));

        final String[] expected = {"TRACE", "DEBUG", "INFO", "INFO", "WARN", "ERROR", "FATAL"};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], tagsOf(i).get("level"), tagsOf(i).get("severity"));
        }
    }

    @Test
    void everyDefinedNumberHasTheLevelOfItsRange() {
        for (final SeverityNumber severity : SeverityNumber.values()) {
            if (severity == SeverityNumber.SEVERITY_NUMBER_UNSPECIFIED || severity == SeverityNumber.UNRECOGNIZED) {
                assertNull(OpenTelemetryLogHandler.levelOf(severity), severity.name());
                continue;
            }
            final String range = severity.name().substring("SEVERITY_NUMBER_".length()).replaceAll("[0-9]$", "");
            assertEquals(range, OpenTelemetryLogHandler.levelOf(severity), severity.name());
        }
    }

    @Test
    void aRepeatedAttributeKeyKeepsTheLastValue() {
        export(LogRecord.newBuilder()
                        .addAttributes(KeyValue.newBuilder().setKey("url.path")
                                               .setValue(AnyValue.newBuilder().setStringValue("/first")))
                        .addAttributes(KeyValue.newBuilder().setKey("url.path")
                                               .setValue(AnyValue.newBuilder().setStringValue("/last"))));

        assertEquals(1, inputs.size(), "the record reaches the analysis instead of failing on the duplicate key");
        assertEquals("/last", tagsOf(0).get("url.path"));
    }

    @Test
    void aRepeatedResourceAttributeKeyKeepsTheLastValue() {
        export(LogRecord.newBuilder(), "", "service.instance.id", "first", "service.instance.id", "last");

        assertEquals(1, inputs.size(), "the request's logs reach the analysis instead of failing on the duplicate key");
        assertEquals("last", inputs.get(0).getServiceInstance());
    }

    @Test
    void aRecordWithoutASeverityNumberGetsNoTag() {
        export(LogRecord.newBuilder());
        export(LogRecord.newBuilder().setSeverityText("INFO"));
        export(LogRecord.newBuilder().setSeverityNumberValue(99));

        for (int i = 0; i < inputs.size(); i++) {
            assertFalse(tagsOf(i).containsKey("severity"), "record " + i + " has no defined severity number");
            assertFalse(tagsOf(i).containsKey("level"), "record " + i + " has no defined severity number");
        }
    }

    @Test
    void attributesNamedSeverityOrLevelAreKept() {
        export(LogRecord.newBuilder()
                        .setSeverityNumber(SeverityNumber.SEVERITY_NUMBER_WARN)
                        .addAttributes(KeyValue.newBuilder().setKey("severity")
                                               .setValue(AnyValue.newBuilder().setStringValue("major")))
                        .addAttributes(KeyValue.newBuilder().setKey("url.path")
                                               .setValue(AnyValue.newBuilder().setStringValue("/api/cart"))));
        export(LogRecord.newBuilder()
                        .setSeverityNumber(SeverityNumber.SEVERITY_NUMBER_WARN)
                        .addAttributes(KeyValue.newBuilder().setKey("level")
                                               .setValue(AnyValue.newBuilder().setStringValue("warning"))));

        assertEquals("major", tagsOf(0).get("severity"));
        assertEquals("WARN", tagsOf(0).get("level"));
        assertEquals("/api/cart", tagsOf(0).get("url.path"));
        assertEquals(3, inputs.get(0).getTags().getDataCount());
        assertEquals("SEVERITY_NUMBER_WARN", tagsOf(1).get("severity"));
        assertEquals("warning", tagsOf(1).get("level"));
    }

    @Test
    void resourceAttributesStayOffTheLog() {
        export(LogRecord.newBuilder(), "level,service.version", "service.version", "3.1.0");

        assertEquals(Set.of(), tagsOf(0).keySet(), "even a resource attribute named in the list is not a tag");
    }

    @Test
    void theLogIsIndexedByTheOtlpList() {
        export(LogRecord.newBuilder(), " level , severity,http.response.status_code ");

        assertEquals(Set.of("level", "severity", "http.response.status_code"),
                     Set.copyOf(metadata.get(0).getSearchableTagKeys()));
    }

    @Test
    void theLogsTagsAreAutocompletedAsOtlpLogs() {
        export(LogRecord.newBuilder());

        assertEquals(TagType.OTLP_LOG, metadata.get(0).getTagType());
    }

    @Test
    void anEmptyOtlpListLeavesTheLogToCoresList() {
        export(LogRecord.newBuilder(), "");

        assertNull(metadata.get(0).getSearchableTagKeys(), "null makes LogBuilder use core's searchableLogsTags");
    }

    private Map<String, String> tagsOf(final int index) {
        return inputs.get(index).getTags().getDataList().stream()
                     .collect(Collectors.toMap(KeyStringValuePair::getKey, KeyStringValuePair::getValue));
    }

    private void export(final LogRecord.Builder record) {
        export(record, "");
    }

    /**
     * @param resourceAttributes key, value, key, value... added to the resource besides {@code service.name}
     */
    private void export(final LogRecord.Builder record, final String searchableTags,
                        final String... resourceAttributes) {
        final OpenTelemetryLogHandler handler = new OpenTelemetryLogHandler();
        final OtelMetricReceiverConfig config = new OtelMetricReceiverConfig();
        config.setOtlpLogSearchableTags(searchableTags);
        handler.init(manager(), config);
        final Resource.Builder resource = Resource.newBuilder().addAttributes(
            KeyValue.newBuilder().setKey("service.name").setValue(AnyValue.newBuilder().setStringValue("checkout")));
        for (int i = 0; i < resourceAttributes.length; i += 2) {
            resource.addAttributes(KeyValue.newBuilder().setKey(resourceAttributes[i])
                                           .setValue(AnyValue.newBuilder().setStringValue(resourceAttributes[i + 1])));
        }
        handler.processExport(ExportLogsServiceRequest.newBuilder().addResourceLogs(
            ResourceLogs.newBuilder()
                        .setResource(resource)
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
