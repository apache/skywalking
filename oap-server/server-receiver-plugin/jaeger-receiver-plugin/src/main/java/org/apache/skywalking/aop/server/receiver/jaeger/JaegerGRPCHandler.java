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

import com.google.gson.JsonObject;
import io.grpc.stub.StreamObserver;
import io.jaegertracing.api_v2.*;
import java.time.Instant;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.source.*;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.receiver.sharing.server.CoreRegisterLinker;
import org.apache.skywalking.oap.server.storage.plugin.jaeger.JaegerSpan;
import org.slf4j.*;

/**
 * @author wusheng
 */
public class JaegerGRPCHandler extends CollectorServiceGrpc.CollectorServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(JaegerGRPCHandler.class);

    private SourceReceiver receiver;
    private JaegerReceiverConfig config;

    public JaegerGRPCHandler(SourceReceiver receiver,
        JaegerReceiverConfig config) {
        this.receiver = receiver;
        this.config = config;
    }

    public void postSpans(Collector.PostSpansRequest request,
        StreamObserver<Collector.PostSpansResponse> responseObserver) {

        request.getBatch().getSpansList().forEach(span -> {
            if (logger.isDebugEnabled()) {
                logger.debug(span.toString());
            }

            JaegerSpan jaegerSpan = new JaegerSpan();
            jaegerSpan.setTraceId(span.getTraceId().toStringUtf8());
            jaegerSpan.setSpanId(span.getSpanId().toStringUtf8());
            Model.Process process = span.getProcess();
            int serviceId = Const.NONE;
            String serviceName = null;
            if (process != null) {
                serviceName = process.getServiceName();
            }
            if (StringUtil.isEmpty(serviceName)) {
                serviceName = "UNKNOWN";
            }
            serviceId = CoreRegisterLinker.getServiceInventoryCache().getServiceId(serviceName);
            if (serviceId != Const.NONE) {
                jaegerSpan.setServiceId(serviceId);
            } else {
                JsonObject properties = new JsonObject();
                if (process != null) {
                    process.getTagsList().forEach(keyValue -> {
                        String key = keyValue.getKey();
                        Model.ValueType valueVType = keyValue.getVType();
                        switch (valueVType) {
                            case STRING:
                                properties.addProperty(key, keyValue.getVStr());
                                break;
                            case INT64:
                                properties.addProperty(key, keyValue.getVInt64());
                                break;
                            case BOOL:
                                properties.addProperty(key, keyValue.getVBool());
                                break;
                            case FLOAT64:
                                properties.addProperty(key, keyValue.getVFloat64());
                                break;
                        }
                    });
                }
                CoreRegisterLinker.getServiceInventoryRegister().getOrCreate(serviceName, properties);
            }

            long duration = span.getDuration().getNanos() / 1_000_000;
            jaegerSpan.setStartTime(Instant.ofEpochSecond(span.getStartTime().getSeconds(), span.getStartTime().getNanos()).toEpochMilli());
            jaegerSpan.setEndTime(jaegerSpan.getStartTime() + duration);
            jaegerSpan.setLatency((int)duration);
            jaegerSpan.setDataBinary(span.toByteArray());

            int finalServiceId = serviceId;
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
                        int endpointId = CoreRegisterLinker.getEndpointInventoryCache().getEndpointId(finalServiceId, endpointName,
                            DetectPoint.SERVER.ordinal());
                        if (endpointId != Const.NONE) {
                            CoreRegisterLinker.getEndpointInventoryRegister().getOrCreate(finalServiceId, endpointName, DetectPoint.SERVER);
                        }
                    }
                }
            });

            receiver.receive(jaegerSpan);
        });

        responseObserver.onNext(Collector.PostSpansResponse.newBuilder().build());
        responseObserver.onCompleted();
    }

}
