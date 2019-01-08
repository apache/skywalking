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

package org.apache.skywalking.oap.server.receiver.istio.telemetry.provider;

import com.google.common.base.Joiner;
import com.google.protobuf.Timestamp;
import io.grpc.stub.StreamObserver;
import io.istio.*;
import io.istio.api.mixer.adapter.model.v1beta1.ReportProto;
import io.istio.api.policy.v1beta1.TypeProto;
import java.time.*;
import java.util.Map;
import org.apache.skywalking.aop.server.receiver.mesh.TelemetryDataDispatcher;
import org.apache.skywalking.apm.network.common.DetectPoint;
import org.apache.skywalking.apm.network.servicemesh.*;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.*;
import org.slf4j.*;

/**
 * Handle istio telemetry data.
 *
 * @author gaohongtao
 */
public class IstioTelemetryGRPCHandler extends HandleMetricServiceGrpc.HandleMetricServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(IstioTelemetryGRPCHandler.class);

    private static final Joiner JOINER = Joiner.on(".");

    private CounterMetric counter;
    private HistogramMetric histogram;

    public IstioTelemetryGRPCHandler(ModuleManager moduleManager) {
        MetricCreator metricCreator = moduleManager.find(TelemetryModule.NAME).provider().getService(MetricCreator.class);
        counter = metricCreator.createCounter("istio_mesh_grpc_in_count", "The count of istio service mesh telemetry",
            MetricTag.EMPTY_KEY, MetricTag.EMPTY_VALUE);
        histogram = metricCreator.createHistogramMetric("istio_mesh_grpc_in_latency", "The process latency of istio service mesh telemetry",
            MetricTag.EMPTY_KEY, MetricTag.EMPTY_VALUE);
    }

    @Override public void handleMetric(IstioMetricProto.HandleMetricRequest request,
        StreamObserver<ReportProto.ReportResult> responseObserver) {
        if (logger.isDebugEnabled()) {
            logger.debug("Received msg {}", request);
        }
        for (IstioMetricProto.InstanceMsg i : request.getInstancesList()) {
            counter.inc();
            HistogramMetric.Timer timer = histogram.createTimer();

            try {
                String requestMethod = string(i, "requestMethod");
                String requestPath = string(i, "requestPath");
                String requestScheme = string(i, "requestScheme");
                long responseCode = int64(i, "responseCode");
                String reporter = string(i, "reporter");
                String protocol = string(i, "apiProtocol");

                String endpoint;
                boolean status = true;
                Protocol netProtocol;
                if (protocol.equals("http") || protocol.equals("https") || requestScheme.equals("http") || requestScheme.equals("https")) {
                    endpoint = requestScheme + "/" + requestMethod + "/" + requestPath;
                    status = responseCode >= 200 && responseCode < 400;
                    netProtocol = Protocol.HTTP;
                } else {
                    //grpc
                    endpoint = protocol + "/" + requestPath;
                    netProtocol = Protocol.gRPC;
                }
                Instant requestTime = time(i, "requestTime");
                Instant responseTime = time(i, "responseTime");
                int latency = Math.toIntExact(Duration.between(requestTime, responseTime).toMillis());

                DetectPoint detectPoint;
                if (reporter.equals("source")) {
                    detectPoint = DetectPoint.client;
                } else {
                    detectPoint = DetectPoint.server;
                }

                String sourceServiceName;
                if (has(i, "sourceNamespace")) {
                    sourceServiceName = JOINER.join(string(i, "sourceService"), string(i, "sourceNamespace"));
                } else {
                    sourceServiceName = string(i, "sourceService");
                }

                String destServiceName;
                if (has(i, "destinationNamespace")) {
                    destServiceName = JOINER.join(string(i, "destinationService"), string(i, "destinationNamespace"));
                } else {
                    destServiceName = string(i, "destinationService");
                }

                ServiceMeshMetric metric = ServiceMeshMetric.newBuilder().setStartTime(requestTime.toEpochMilli())
                    .setEndTime(responseTime.toEpochMilli()).setSourceServiceName(sourceServiceName)
                    .setSourceServiceInstance(string(i, "sourceUID")).setDestServiceName(destServiceName)
                    .setDestServiceInstance(string(i, "destinationUID")).setEndpoint(endpoint).setLatency(latency)
                    .setResponseCode(Math.toIntExact(responseCode)).setStatus(status).setProtocol(netProtocol).setDetectPoint(detectPoint).build();
                logger.debug("Transformed metric {}", metric);

                TelemetryDataDispatcher.preProcess(metric);
            } finally {
                timer.finish();
            }
        }
        responseObserver.onNext(ReportProto.ReportResult.newBuilder().build());
        responseObserver.onCompleted();
    }

    private String string(final IstioMetricProto.InstanceMsg instanceMsg, final String key) {
        Map<String, TypeProto.Value> map = instanceMsg.getDimensionsMap();
        assertDimension(map, key);
        return map.get(key).getStringValue();
    }

    private long int64(final IstioMetricProto.InstanceMsg instanceMsg, final String key) {
        Map<String, TypeProto.Value> map = instanceMsg.getDimensionsMap();
        assertDimension(map, key);
        return map.get(key).getInt64Value();
    }

    private Instant time(final IstioMetricProto.InstanceMsg instanceMsg, final String key) {
        Map<String, TypeProto.Value> map = instanceMsg.getDimensionsMap();
        assertDimension(map, key);
        Timestamp timestamp = map.get(key).getTimestampValue().getValue();
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    private void assertDimension(final Map<String, TypeProto.Value> map, final String key) {
        if (!map.containsKey(key)) {
            throw new IllegalArgumentException(String.format("Lack dimension %s", key));
        }
    }

    private boolean has(final IstioMetricProto.InstanceMsg instanceMsg, final String key) {
        Map<String, TypeProto.Value> map = instanceMsg.getDimensionsMap();
        return map.containsKey(key);
    }
}
