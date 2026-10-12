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
 */

package org.apache.skywalking.oap.server.receiver.otel.otlp;

import com.google.protobuf.util.JsonFormat;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.metrics.v1.Exemplar;
import io.opentelemetry.proto.trace.v1.Span;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * OTLP/JSON spells ids in hex, where the standard protobuf JSON mapping expects base64.
 */
class OTLPJsonIdsTest {
    private static final String TRACE_ID = "5b8efff798038103d269b633813fc60c";
    private static final String SPAN_ID = "eee19b7ec3c1b174";
    private static final String PARENT_ID = "eee19b7ec3c1b173";

    @Test
    void spanAndLinkIdsAreReadAsTheSenderWroteThem() throws Exception {
        final ExportTraceServiceRequest.Builder builder = ExportTraceServiceRequest.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(OTLPValues.hexIdsToBase64(
            "{\"resourceSpans\":[{\"scopeSpans\":[{\"spans\":[{\"traceId\":\"" + TRACE_ID + "\",\"spanId\":\""
                + SPAN_ID + "\",\"parentSpanId\":\"" + PARENT_ID + "\",\"name\":\"GET\",\"links\":[{\"traceId\":\""
                + TRACE_ID + "\",\"spanId\":\"" + PARENT_ID + "\"}]}]}]}]}"), builder);
        final Span span = builder.getResourceSpans(0).getScopeSpans(0).getSpans(0);
        assertEquals(TRACE_ID, OTLPValues.hexId(span.getTraceId()));
        assertEquals(SPAN_ID, OTLPValues.hexId(span.getSpanId()));
        assertEquals(PARENT_ID, OTLPValues.hexId(span.getParentSpanId()));
        assertEquals(PARENT_ID, OTLPValues.hexId(span.getLinks(0).getSpanId()));
        assertEquals("GET", span.getName(), "everything else is untouched");
    }

    @Test
    void logRecordAndExemplarIdsToo() throws Exception {
        final ExportLogsServiceRequest.Builder logs = ExportLogsServiceRequest.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(OTLPValues.hexIdsToBase64(
            "{\"resourceLogs\":[{\"scopeLogs\":[{\"logRecords\":[{\"traceId\":\"" + TRACE_ID + "\",\"spanId\":\""
                + SPAN_ID + "\"}]}]}]}"), logs);
        final LogRecord log = logs.getResourceLogs(0).getScopeLogs(0).getLogRecords(0);
        assertEquals(TRACE_ID, OTLPValues.hexId(log.getTraceId()));
        assertEquals(SPAN_ID, OTLPValues.hexId(log.getSpanId()));

        final ExportMetricsServiceRequest.Builder metrics = ExportMetricsServiceRequest.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(OTLPValues.hexIdsToBase64(
            "{\"resourceMetrics\":[{\"scopeMetrics\":[{\"metrics\":[{\"name\":\"m\",\"sum\":{\"dataPoints\":"
                + "[{\"asInt\":\"1\",\"exemplars\":[{\"traceId\":\"" + TRACE_ID + "\",\"spanId\":\"" + SPAN_ID
                + "\"}]}]}}]}]}]}"), metrics);
        final Exemplar exemplar = metrics.getResourceMetrics(0).getScopeMetrics(0).getMetrics(0).getSum()
                                         .getDataPoints(0).getExemplars(0);
        assertEquals(TRACE_ID, OTLPValues.hexId(exemplar.getTraceId()));
        assertEquals(SPAN_ID, OTLPValues.hexId(exemplar.getSpanId()));
    }

    @Test
    void anIdAlreadyInBase64AndABodyThatIsNotJsonAreLeftAlone() {
        // 16 bytes of base64 end in `==`, which is not hex.
        final String base64 = "{\"traceId\":\"W47/95gDgQPSabYzgT/GDA==\"}";
        assertEquals(base64, OTLPValues.hexIdsToBase64(base64));
        assertEquals("not json {", OTLPValues.hexIdsToBase64("not json {"));
    }
}
