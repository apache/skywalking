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

package org.apache.skywalking.aop.server.receiver.jaeger;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import io.jaegertracing.api_v2.Collector;
import io.jaegertracing.api_v2.CollectorServiceGrpc;
import io.jaegertracing.api_v2.Model;
import java.time.Instant;
import java.util.Base64;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.storage.plugin.jaeger.JaegerSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JaegerGRPCHandler extends CollectorServiceGrpc.CollectorServiceImplBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(JaegerGRPCHandler.class);

    private SourceReceiver receiver;
    private JaegerReceiverConfig config;

    public JaegerGRPCHandler(SourceReceiver receiver, JaegerReceiverConfig config) {
        this.receiver = receiver;
        this.config = config;
    }

    public void postSpans(Collector.PostSpansRequest request,
                          StreamObserver<Collector.PostSpansResponse> responseObserver) {

        request.getBatch().getSpansList().forEach(span -> {
            try {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(span.toString());
                }

                JaegerSpan jaegerSpan = new JaegerSpan();
                jaegerSpan.setTraceId(format(span.getTraceId()));
                jaegerSpan.setSpanId(format(span.getSpanId()));
                Model.Process process = span.getProcess();
                String serviceName = null;
                if (process != null) {
                    serviceName = process.getServiceName();
                }
                if (StringUtil.isEmpty(serviceName)) {
                    serviceName = "UNKNOWN";
                }
                final String serviceId = IDManager.ServiceID.buildId(serviceName, NodeType.Normal);

                long duration = span.getDuration().getNanos() / 1_000_000;
                jaegerSpan.setStartTime(Instant.ofEpochSecond(
                    span.getStartTime().getSeconds(), span.getStartTime().getNanos()).toEpochMilli());
                long timeBucket = TimeBucket.getRecordTimeBucket(jaegerSpan.getStartTime());
                jaegerSpan.setTimeBucket(timeBucket);
                jaegerSpan.setEndTime(jaegerSpan.getStartTime() + duration);
                jaegerSpan.setLatency((int) duration);
                jaegerSpan.setDataBinary(span.toByteArray());
                jaegerSpan.setEndpointName(span.getOperationName());
                jaegerSpan.setServiceId(serviceId);

                span.getTagsList().forEach(tag -> {
                    String key = tag.getKey();
                    if ("error".equals(key)) {
                        boolean status = tag.getVBool();
                        jaegerSpan.setIsError(BooleanUtils.booleanToValue(status));
                    } else if ("span.kind".equals(key)) {
                        String kind = tag.getVStr();
                        if ("server".equals(kind) || "consumer".equals(kind)) {
                            String endpointName = span.getOperationName();
                            jaegerSpan.setEndpointName(endpointName);
                            jaegerSpan.setEndpointId(
                                IDManager.EndpointID.buildId(serviceId, endpointName));
                        }
                    }
                });

                receiver.receive(jaegerSpan);
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        });

        responseObserver.onNext(Collector.PostSpansResponse.newBuilder().build());
        responseObserver.onCompleted();
    }

    private String format(ByteString bytes) {
        Base64.Encoder encoder = Base64.getEncoder();
        return encoder.encodeToString(bytes.toByteArray());
    }

}
