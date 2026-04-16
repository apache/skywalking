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
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OTLP/HTTP handler for log data. Supports both protobuf and JSON encoding.
 * Delegates processing to {@link OpenTelemetryLogHandler#processExport}.
 */
@Slf4j
@RequiredArgsConstructor
public class OpenTelemetryLogHTTPHandler {
    private static final byte[] EMPTY_RESPONSE =
        ExportLogsServiceResponse.getDefaultInstance().toByteArray();

    private final OpenTelemetryLogHandler logHandler;

    @Blocking
    @Post("/v1/logs")
    @Consumes("application/x-protobuf")
    public HttpResponse collectProtobuf(AggregatedHttpRequest request) {
        try {
            final ExportLogsServiceRequest exportRequest =
                ExportLogsServiceRequest.parseFrom(request.content().array());
            logHandler.processExport(exportRequest);
            return HttpResponse.of(HttpStatus.OK, MediaType.PROTOBUF, EMPTY_RESPONSE);
        } catch (InvalidProtocolBufferException e) {
            log.warn("Failed to parse OTLP/HTTP log request", e);
            return HttpResponse.of(HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("Failed to process OTLP/HTTP log request", e);
            return HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Blocking
    @Post("/v1/logs")
    @ConsumesJson
    public HttpResponse collectJson(AggregatedHttpRequest request) {
        try {
            final ExportLogsServiceRequest.Builder builder =
                ExportLogsServiceRequest.newBuilder();
            JsonFormat.parser().ignoringUnknownFields().merge(
                request.contentUtf8(), builder);
            logHandler.processExport(builder.build());
            return HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8, "{}");
        } catch (InvalidProtocolBufferException e) {
            log.warn("Failed to parse OTLP/HTTP JSON log request", e);
            return HttpResponse.of(HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("Failed to process OTLP/HTTP JSON log request", e);
            return HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
