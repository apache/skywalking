/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.receiver.aws.firehose;

import com.google.protobuf.InvalidProtocolBufferException;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.server.annotation.ConsumesJson;
import com.linecorp.armeria.server.annotation.Default;
import com.linecorp.armeria.server.annotation.Header;
import com.linecorp.armeria.server.annotation.Post;
import com.linecorp.armeria.server.annotation.ProducesJson;
import io.opentelemetry.proto.collector.metrics.firehose.v0_7.ExportMetricsServiceRequest;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.receiver.otel.otlp.OpenTelemetryMetricRequestProcessor;

@Slf4j
@AllArgsConstructor
public class FirehoseHTTPHandler {
    private final OpenTelemetryMetricRequestProcessor openTelemetryMetricRequestProcessor;
    private final String firehoseAccessKey;

    @Post("/aws/firehose/metrics")
    @ConsumesJson
    @ProducesJson
    public HttpResponse collectMetrics(final FirehoseReq firehoseReq,
                                       @Default @Header(value = "X-Amz-Firehose-Access-Key") String accessKey) {

        if (StringUtil.isNotBlank(firehoseAccessKey) && !firehoseAccessKey.equals(accessKey)) {
            return HttpResponse.ofJson(
                HttpStatus.UNAUTHORIZED,
                new FirehoseRes(firehoseReq.getRequestId(), System.currentTimeMillis(),
                                "AccessKey incorrect, please check your config"
                )
            );
        }

        try {
            for (RequestData record : firehoseReq.getRecords()) {
                final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(
                    Base64.getDecoder().decode(record.getData()));
                ExportMetricsServiceRequest request;
                while ((request = ExportMetricsServiceRequest.parseDelimitedFrom(byteArrayInputStream)) != null) {
                    openTelemetryMetricRequestProcessor.processMetricsRequest(
                        OtelMetricsConvertor.convertExportMetricsRequest(request));
                }
            }
        } catch (InvalidProtocolBufferException e) {
            log.warn("Only OpenTelemetry format is accepted", e);
            return HttpResponse.ofJson(
                HttpStatus.BAD_REQUEST,
                new FirehoseRes(firehoseReq.getRequestId(), System.currentTimeMillis(),
                                "Only OpenTelemetry format is accepted"
                )
            );
        } catch (Exception e) {
            log.error("Server error", e);
            return HttpResponse.ofJson(
                HttpStatus.INTERNAL_SERVER_ERROR,
                new FirehoseRes(firehoseReq.getRequestId(), System.currentTimeMillis(),
                                "Server error, please check the OAP log"
                )
            );
        }
        return HttpResponse.ofJson(
            HttpStatus.OK,
            new FirehoseRes(firehoseReq.getRequestId(), System.currentTimeMillis(), null)
        );
    }

}
