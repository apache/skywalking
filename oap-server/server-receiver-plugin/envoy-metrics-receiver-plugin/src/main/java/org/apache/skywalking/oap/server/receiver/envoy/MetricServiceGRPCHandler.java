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

package org.apache.skywalking.oap.server.receiver.envoy;

import io.envoyproxy.envoy.api.v2.core.Node;
import io.envoyproxy.envoy.service.metrics.v2.*;
import io.grpc.stub.StreamObserver;
import io.prometheus.client.Metrics;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.slf4j.*;

/**
 * @author wusheng
 */
public class MetricServiceGRPCHandler extends MetricsServiceGrpc.MetricsServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(MetricServiceGRPCHandler.class);

    public MetricServiceGRPCHandler(ModuleManager moduleManager
    ) {
    }

    @Override
    public StreamObserver<StreamMetricsMessage> streamMetrics(StreamObserver<StreamMetricsResponse> responseObserver) {
        return new StreamObserver<StreamMetricsMessage>() {
            private boolean isFirst = true;
            private String serviceName = null;

            @Override public void onNext(StreamMetricsMessage message) {
                if (isFirst) {
                    isFirst = false;
                    StreamMetricsMessage.Identifier identifier = message.getIdentifier();
                    Node node = identifier.getNode();
                    if (node != null) {
                        String nodeId = node.getId();
                        if (!StringUtil.isEmpty(nodeId)) {
                            serviceName = nodeId;
                            String cluster = node.getCluster();
                            if (!StringUtil.isEmpty(cluster)) {
                                serviceName = nodeId + "." + cluster;
                            }
                        }
                    }
                }

                if (serviceName != null) {
                    for (Metrics.MetricFamily metricFamily : message.getEnvoyMetricsList()) {

                    }
                }
            }

            @Override public void onError(Throwable throwable) {

            }

            @Override public void onCompleted() {

            }
        };
    }
}
