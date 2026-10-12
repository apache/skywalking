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

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.annotation.Blocking;
import com.linecorp.armeria.server.annotation.Consumes;
import com.linecorp.armeria.server.annotation.ConsumesJson;
import com.linecorp.armeria.server.annotation.Post;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OTLP/HTTP handler for metric data. Supports both protobuf and JSON encoding.
 * Delegates processing to {@link OpenTelemetryMetricRequestProcessor#processMetricsRequest}.
 */
@Slf4j
@RequiredArgsConstructor
public class OpenTelemetryMetricHTTPHandler {
    private static final byte[] EMPTY_RESPONSE =
        ExportMetricsServiceResponse.getDefaultInstance().toByteArray();

    private final OpenTelemetryMetricRequestProcessor metricProcessor;

    @Blocking
    @Post("/v1/metrics")
    @Consumes("application/x-protobuf")
    public HttpResponse collectProtobuf(AggregatedHttpRequest request) {
        try {
            final ExportMetricsServiceRequest exportRequest =
                ExportMetricsServiceRequest.parseFrom(request.content().array());
            metricProcessor.processMetricsRequest(exportRequest);
            return HttpResponse.of(HttpStatus.OK, MediaType.PROTOBUF, EMPTY_RESPONSE);
        } catch (InvalidProtocolBufferException e) {
            log.warn("Failed to parse OTLP/HTTP metric request", e);
            return HttpResponse.of(HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("Failed to process OTLP/HTTP metric request", e);
            return HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Blocking
    @Post("/v1/metrics")
    @ConsumesJson
    public HttpResponse collectJson(AggregatedHttpRequest request) {
        try {
            final ExportMetricsServiceRequest.Builder builder =
                ExportMetricsServiceRequest.newBuilder();
            JsonFormat.parser().ignoringUnknownFields().merge(
                request.contentUtf8(), builder);
            metricProcessor.processMetricsRequest(builder.build());
            return HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8, "{}");
        } catch (InvalidProtocolBufferException e) {
            log.warn("Failed to parse OTLP/HTTP JSON metric request", e);
            return HttpResponse.of(HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("Failed to process OTLP/HTTP JSON metric request", e);
            return HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
