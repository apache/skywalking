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
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OTLP/HTTP handler for trace data. Supports both protobuf and JSON encoding.
 * Delegates processing to {@link OpenTelemetryTraceHandler#processExport}.
 */
@Slf4j
@RequiredArgsConstructor
public class OpenTelemetryTraceHTTPHandler {
    private static final byte[] EMPTY_RESPONSE =
        ExportTraceServiceResponse.getDefaultInstance().toByteArray();

    private final OpenTelemetryTraceHandler traceHandler;

    @Blocking
    @Post("/v1/traces")
    @Consumes("application/x-protobuf")
    public HttpResponse collectProtobuf(AggregatedHttpRequest request) {
        try {
            final ExportTraceServiceRequest exportRequest =
                ExportTraceServiceRequest.parseFrom(request.content().array());
            traceHandler.processExport(exportRequest);
            return HttpResponse.of(HttpStatus.OK, MediaType.PROTOBUF, EMPTY_RESPONSE);
        } catch (InvalidProtocolBufferException e) {
            log.warn("Failed to parse OTLP/HTTP trace request", e);
            return HttpResponse.of(HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("Failed to process OTLP/HTTP trace request", e);
            return HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Blocking
    @Post("/v1/traces")
    @ConsumesJson
    public HttpResponse collectJson(AggregatedHttpRequest request) {
        try {
            final ExportTraceServiceRequest.Builder builder =
                ExportTraceServiceRequest.newBuilder();
            JsonFormat.parser().ignoringUnknownFields().merge(
                request.contentUtf8(), builder);
            traceHandler.processExport(builder.build());
            return HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8, "{}");
        } catch (InvalidProtocolBufferException e) {
            log.warn("Failed to parse OTLP/HTTP JSON trace request", e);
            return HttpResponse.of(HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("Failed to process OTLP/HTTP JSON trace request", e);
            return HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
