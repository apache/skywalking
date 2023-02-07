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

package org.apache.skywalking.aop.server.receiver.mesh;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.servicemesh.v3.HTTPServiceMeshMetric;
import org.apache.skywalking.apm.network.servicemesh.v3.Protocol;
import org.apache.skywalking.apm.network.servicemesh.v3.ServiceMeshMetrics;
import org.apache.skywalking.apm.network.servicemesh.v3.TCPServiceMeshMetric;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.source.Endpoint;
import org.apache.skywalking.oap.server.core.source.RequestType;
import org.apache.skywalking.oap.server.core.source.Service;
import org.apache.skywalking.oap.server.core.source.ServiceInstance;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceRelation;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceUpdate;
import org.apache.skywalking.oap.server.core.source.ServiceRelation;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.source.TCPService;
import org.apache.skywalking.oap.server.core.source.TCPServiceInstance;
import org.apache.skywalking.oap.server.core.source.TCPServiceInstanceRelation;
import org.apache.skywalking.oap.server.core.source.TCPServiceInstanceUpdate;
import org.apache.skywalking.oap.server.core.source.TCPServiceRelation;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;
import com.google.gson.JsonObject;

/**
 * TelemetryDataDispatcher processes the {@link ServiceMeshMetrics} format telemetry data, transfers it to source
 * dispatcher.
 */
@Slf4j
public class TelemetryDataDispatcher {
    private static final int TCP_COMPONENT = 110; // Defined in component-libraries.yml

    private static SourceReceiver SOURCE_RECEIVER;
    private static NamingControl NAME_LENGTH_CONTROL;
    private static HistogramMetrics MESH_ANALYSIS_METRICS;
    private static CounterMetrics MESH_ERROR_METRICS;

    private TelemetryDataDispatcher() {
    }

    public static void init(ModuleManager moduleManager) {
        SOURCE_RECEIVER = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
        MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME)
                                                     .provider()
                                                     .getService(MetricsCreator.class);
        NAME_LENGTH_CONTROL = moduleManager.find(CoreModule.NAME)
                                           .provider()
                                           .getService(NamingControl.class);
        MESH_ANALYSIS_METRICS = metricsCreator.createHistogramMetric(
            "mesh_analysis_latency", "The process latency of service mesh telemetry", MetricsTag.EMPTY_KEY,
            MetricsTag.EMPTY_VALUE
        );
        MESH_ERROR_METRICS = metricsCreator.createCounter("mesh_analysis_error_count",
                                                          "The error number of mesh analysis",
                                                          MetricsTag.EMPTY_KEY,
                                                          MetricsTag.EMPTY_VALUE
        );
    }

    public static void process(ServiceMeshMetrics metrics) {
        dispatchHTTPMetrics(metrics);
        dispatchTCPMetrics(metrics);
    }

    private static void dispatchTCPMetrics(ServiceMeshMetrics metrics) {
        metrics.getTcpMetrics().getMetricsList().forEach(m -> {
            final TCPServiceMeshMetric.Builder data = m.toBuilder();
            try (HistogramMetrics.Timer ignored = MESH_ANALYSIS_METRICS.createTimer()) {
                if (data.getSourceServiceName() != null) {
                    data.setSourceServiceName(
                        NAME_LENGTH_CONTROL.formatServiceName(data.getSourceServiceName()));
                }
                if (data.getSourceServiceInstance() != null) {
                    data.setSourceServiceInstance(
                        NAME_LENGTH_CONTROL.formatInstanceName(data.getSourceServiceInstance()));
                }
                if (data.getDestServiceName() != null) {
                    data.setDestServiceName(
                        NAME_LENGTH_CONTROL.formatServiceName(data.getDestServiceName()));
                }
                if (data.getDestServiceInstance() != null) {
                    data.setDestServiceInstance(
                        NAME_LENGTH_CONTROL.formatInstanceName(data.getDestServiceInstance()));
                }
                if (data.getInternalErrorCode() == null) {
                    // Add this since 8.2.0, set the default value.
                    data.setInternalErrorCode(Const.EMPTY_STRING);
                }

                dispatchTCPMetrics(data);
            } catch (Exception e) {
                MESH_ERROR_METRICS.inc();
                log.error(e.getMessage(), e);
            }
        });
    }

    private static void dispatchHTTPMetrics(ServiceMeshMetrics metrics) {
        metrics.getHttpMetrics().getMetricsList().forEach(m -> {
            final HTTPServiceMeshMetric.Builder data = m.toBuilder();
            try (HistogramMetrics.Timer ignored = MESH_ANALYSIS_METRICS.createTimer()) {
                if (data.getSourceServiceName() != null) {
                    data.setSourceServiceName(
                        NAME_LENGTH_CONTROL.formatServiceName(data.getSourceServiceName()));
                }
                if (data.getSourceServiceInstance() != null) {
                    data.setSourceServiceInstance(
                        NAME_LENGTH_CONTROL.formatInstanceName(data.getSourceServiceInstance()));
                }
                if (data.getDestServiceName() != null) {
                    data.setDestServiceName(
                        NAME_LENGTH_CONTROL.formatServiceName(data.getDestServiceName()));
                }
                if (data.getDestServiceInstance() != null) {
                    data.setDestServiceInstance(
                        NAME_LENGTH_CONTROL.formatInstanceName(data.getDestServiceInstance()));
                }
                if (data.getEndpoint() != null) {
                    data.setEndpoint(NAME_LENGTH_CONTROL
                        .formatEndpointName(data.getDestServiceName(), data.getEndpoint()));
                }
                if (data.getInternalErrorCode() == null) {
                    // Add this since 8.2.0, set the default value.
                    data.setInternalErrorCode(Const.EMPTY_STRING);
                }

                dispatchHTTPMetrics(data);
            } catch (Exception e) {
                MESH_ERROR_METRICS.inc();
                log.error(e.getMessage(), e);
            }
        });
    }

    private static void dispatchTCPMetrics(TCPServiceMeshMetric.Builder metrics) {
        long minuteTimeBucket = TimeBucket.getMinuteTimeBucket(metrics.getStartTime());

        if (org.apache.skywalking.apm.network.common.v3.DetectPoint.server.equals(metrics.getDetectPoint())) {
            toTCPService(metrics, minuteTimeBucket);
            toTCPServiceInstance(metrics, minuteTimeBucket);
            toTCPServiceInstanceTraffic(metrics, minuteTimeBucket);
        }

        String sourceService = metrics.getSourceServiceName();
        // Don't generate relation, if no source.
        if (StringUtil.isNotEmpty(sourceService)) {
            toTCPServiceRelation(metrics, minuteTimeBucket);
            toTCPServiceInstanceRelation(metrics, minuteTimeBucket);
        }
    }

    static void dispatchHTTPMetrics(HTTPServiceMeshMetric.Builder metrics) {
        long minuteTimeBucket = TimeBucket.getMinuteTimeBucket(metrics.getStartTime());

        if (org.apache.skywalking.apm.network.common.v3.DetectPoint.server.equals(metrics.getDetectPoint())) {
            toService(metrics, minuteTimeBucket);
            toServiceInstance(metrics, minuteTimeBucket);
            toServiceInstanceTraffic(metrics, minuteTimeBucket);
            toEndpoint(metrics, minuteTimeBucket);
        }

        String sourceService = metrics.getSourceServiceName();
        // Don't generate relation, if no source.
        if (StringUtil.isNotEmpty(sourceService)) {
            toServiceRelation(metrics, minuteTimeBucket);
            toServiceInstanceRelation(metrics, minuteTimeBucket);
        }
    }

    private static void toService(HTTPServiceMeshMetric.Builder metrics, long minuteTimeBucket) {
        Service service = new Service();
        service.setTimeBucket(minuteTimeBucket);
        service.setName(metrics.getDestServiceName());
        service.setLayer(Layer.MESH);
        service.setServiceInstanceName(metrics.getDestServiceInstance());
        service.setEndpointName(metrics.getEndpoint());
        service.setLatency(metrics.getLatency());
        service.setStatus(metrics.getStatus());
        service.setHttpResponseStatusCode(metrics.getResponseCode());
        service.setType(protocol2Type(metrics.getProtocol()));
        service.getSideCar().setInternalErrorCode(metrics.getInternalErrorCode());
        service.getSideCar().setInternalRequestLatencyNanos(metrics.getInternalRequestLatencyNanos());
        service.getSideCar().setInternalResponseLatencyNanos(metrics.getInternalResponseLatencyNanos());

        SOURCE_RECEIVER.receive(service);
    }

    private static void toTCPService(TCPServiceMeshMetric.Builder metrics, long minuteTimeBucket) {
        TCPService service = new TCPService();
        service.setTimeBucket(minuteTimeBucket);
        service.setName(metrics.getDestServiceName());
        service.setLayer(Layer.MESH);
        service.setServiceInstanceName(metrics.getDestServiceInstance());
        service.getSideCar().setInternalErrorCode(metrics.getInternalErrorCode());
        service.getSideCar().setInternalRequestLatencyNanos(metrics.getInternalRequestLatencyNanos());
        service.getSideCar().setInternalResponseLatencyNanos(metrics.getInternalResponseLatencyNanos());
        service.setReceivedBytes(metrics.getReceivedBytes());
        service.setSentBytes(metrics.getSentBytes());

        SOURCE_RECEIVER.receive(service);
    }

    private static void toServiceRelation(HTTPServiceMeshMetric.Builder metrics, long minuteTimeBucket) {
        ServiceRelation serviceRelation = new ServiceRelation();
        serviceRelation.setTimeBucket(minuteTimeBucket);
        serviceRelation.setSourceServiceName(metrics.getSourceServiceName());
        serviceRelation.setSourceLayer(Layer.MESH);
        serviceRelation.setSourceServiceInstanceName(metrics.getSourceServiceInstance());
        serviceRelation.setDestServiceName(metrics.getDestServiceName());
        serviceRelation.setDestLayer(Layer.MESH);
        serviceRelation.setDestServiceInstanceName(metrics.getDestServiceInstance());
        serviceRelation.setEndpoint(metrics.getEndpoint());
        serviceRelation.setLatency(metrics.getLatency());
        serviceRelation.setStatus(metrics.getStatus());
        serviceRelation.setType(protocol2Type(metrics.getProtocol()));
        serviceRelation.setHttpResponseStatusCode(metrics.getResponseCode());
        serviceRelation.setDetectPoint(detectPointMapping(metrics.getDetectPoint()));
        serviceRelation.setComponentId(protocol2Component(metrics.getProtocol()));
        serviceRelation.setTlsMode(metrics.getTlsMode());
        serviceRelation.getSideCar().setInternalErrorCode(metrics.getInternalErrorCode());
        serviceRelation.getSideCar().setInternalRequestLatencyNanos(metrics.getInternalRequestLatencyNanos());
        serviceRelation.getSideCar().setInternalResponseLatencyNanos(metrics.getInternalResponseLatencyNanos());

        SOURCE_RECEIVER.receive(serviceRelation);
    }

    private static void toTCPServiceRelation(TCPServiceMeshMetric.Builder metrics, long minuteTimeBucket) {
        TCPServiceRelation serviceRelation = new TCPServiceRelation();
        serviceRelation.setTimeBucket(minuteTimeBucket);
        serviceRelation.setSourceServiceName(metrics.getSourceServiceName());
        serviceRelation.setSourceLayer(Layer.MESH);
        serviceRelation.setSourceServiceInstanceName(metrics.getSourceServiceInstance());
        serviceRelation.setDestServiceName(metrics.getDestServiceName());
        serviceRelation.setDestLayer(Layer.MESH);
        serviceRelation.setDestServiceInstanceName(metrics.getDestServiceInstance());
        serviceRelation.setDetectPoint(detectPointMapping(metrics.getDetectPoint()));
        serviceRelation.setComponentId(TCP_COMPONENT);
        serviceRelation.setTlsMode(metrics.getTlsMode());
        serviceRelation.getSideCar().setInternalErrorCode(metrics.getInternalErrorCode());
        serviceRelation.getSideCar().setInternalRequestLatencyNanos(metrics.getInternalRequestLatencyNanos());
        serviceRelation.getSideCar().setInternalResponseLatencyNanos(metrics.getInternalResponseLatencyNanos());
        serviceRelation.setReceivedBytes(metrics.getReceivedBytes());
        serviceRelation.setSentBytes(metrics.getSentBytes());

        SOURCE_RECEIVER.receive(serviceRelation);
    }

    private static void toServiceInstance(HTTPServiceMeshMetric.Builder metrics, long minuteTimeBucket) {
        ServiceInstance serviceInstance = new ServiceInstance();
        serviceInstance.setTimeBucket(minuteTimeBucket);
        serviceInstance.setName(metrics.getDestServiceInstance());
        serviceInstance.setServiceName(metrics.getDestServiceName());
        serviceInstance.setServiceLayer(Layer.MESH);
        serviceInstance.setEndpointName(metrics.getEndpoint());
        serviceInstance.setLatency(metrics.getLatency());
        serviceInstance.setStatus(metrics.getStatus());
        serviceInstance.setHttpResponseStatusCode(metrics.getResponseCode());
        serviceInstance.setType(protocol2Type(metrics.getProtocol()));
        serviceInstance.getSideCar().setInternalErrorCode(metrics.getInternalErrorCode());
        serviceInstance.getSideCar().setInternalRequestLatencyNanos(metrics.getInternalRequestLatencyNanos());
        serviceInstance.getSideCar().setInternalResponseLatencyNanos(metrics.getInternalResponseLatencyNanos());

        SOURCE_RECEIVER.receive(serviceInstance);
    }

    private static void toTCPServiceInstance(TCPServiceMeshMetric.Builder metrics, long minuteTimeBucket) {
        TCPServiceInstance serviceInstance = new TCPServiceInstance();
        serviceInstance.setTimeBucket(minuteTimeBucket);
        serviceInstance.setName(metrics.getDestServiceInstance());
        serviceInstance.setServiceName(metrics.getDestServiceName());
        serviceInstance.setServiceLayer(Layer.MESH);
        serviceInstance.getSideCar().setInternalErrorCode(metrics.getInternalErrorCode());
        serviceInstance.getSideCar().setInternalRequestLatencyNanos(metrics.getInternalRequestLatencyNanos());
        serviceInstance.getSideCar().setInternalResponseLatencyNanos(metrics.getInternalResponseLatencyNanos());
        serviceInstance.setReceivedBytes(metrics.getReceivedBytes());
        serviceInstance.setSentBytes(metrics.getSentBytes());

        SOURCE_RECEIVER.receive(serviceInstance);
    }

    private static void toServiceInstanceTraffic(HTTPServiceMeshMetric.Builder metrics, long minuteTimeBucket) {
        ServiceInstanceUpdate instanceTraffic = new ServiceInstanceUpdate();
        instanceTraffic.setTimeBucket(minuteTimeBucket);
        instanceTraffic.setName(metrics.getDestServiceInstance());
        instanceTraffic.setServiceId(IDManager.ServiceID.buildId(metrics.getDestServiceName(), true));
        if (metrics.getDestInstancePropertiesList() != null) {
            final JsonObject properties = new JsonObject();
            metrics
                .getDestInstancePropertiesList()
                .stream()
                .forEach(it -> properties.addProperty(it.getKey(), it.getValue()));
            instanceTraffic.setProperties(properties);
        }
        SOURCE_RECEIVER.receive(instanceTraffic);
    }

    private static void toTCPServiceInstanceTraffic(TCPServiceMeshMetric.Builder metrics, long minuteTimeBucket) {
        TCPServiceInstanceUpdate instanceTraffic = new TCPServiceInstanceUpdate();
        instanceTraffic.setTimeBucket(minuteTimeBucket);
        instanceTraffic.setName(metrics.getDestServiceInstance());
        instanceTraffic.setServiceId(IDManager.ServiceID.buildId(metrics.getDestServiceName(), true));
        if (metrics.getDestInstancePropertiesList() != null) {
            final JsonObject properties = new JsonObject();
            metrics
                .getDestInstancePropertiesList()
                .stream()
                .forEach(it -> properties.addProperty(it.getKey(), it.getValue()));
            instanceTraffic.setProperties(properties);
        }
        SOURCE_RECEIVER.receive(instanceTraffic);
    }

    private static void toServiceInstanceRelation(HTTPServiceMeshMetric.Builder metrics, long minuteTimeBucket) {
        ServiceInstanceRelation serviceRelation = new ServiceInstanceRelation();
        serviceRelation.setTimeBucket(minuteTimeBucket);
        serviceRelation.setSourceServiceInstanceName(metrics.getSourceServiceInstance());
        serviceRelation.setSourceServiceName(metrics.getSourceServiceName());
        serviceRelation.setSourceServiceLayer(Layer.MESH);
        serviceRelation.setDestServiceInstanceName(metrics.getDestServiceInstance());
        serviceRelation.setDestServiceLayer(Layer.MESH);
        serviceRelation.setDestServiceName(metrics.getDestServiceName());
        serviceRelation.setEndpoint(metrics.getEndpoint());
        serviceRelation.setLatency(metrics.getLatency());
        serviceRelation.setStatus(metrics.getStatus());
        serviceRelation.setType(protocol2Type(metrics.getProtocol()));
        serviceRelation.setResponseCode(metrics.getResponseCode());
        serviceRelation.setHttpResponseStatusCode(metrics.getResponseCode());
        serviceRelation.setDetectPoint(detectPointMapping(metrics.getDetectPoint()));
        serviceRelation.setComponentId(protocol2Component(metrics.getProtocol()));
        serviceRelation.setTlsMode(metrics.getTlsMode());
        serviceRelation.getSideCar().setInternalErrorCode(metrics.getInternalErrorCode());
        serviceRelation.getSideCar().setInternalRequestLatencyNanos(metrics.getInternalRequestLatencyNanos());
        serviceRelation.getSideCar().setInternalResponseLatencyNanos(metrics.getInternalResponseLatencyNanos());

        SOURCE_RECEIVER.receive(serviceRelation);
    }

    private static void toTCPServiceInstanceRelation(TCPServiceMeshMetric.Builder metrics, long minuteTimeBucket) {
        TCPServiceInstanceRelation serviceRelation = new TCPServiceInstanceRelation();
        serviceRelation.setTimeBucket(minuteTimeBucket);
        serviceRelation.setSourceServiceInstanceName(metrics.getSourceServiceInstance());
        serviceRelation.setSourceServiceName(metrics.getSourceServiceName());
        serviceRelation.setSourceServiceLayer(Layer.MESH);
        serviceRelation.setDestServiceInstanceName(metrics.getDestServiceInstance());
        serviceRelation.setDestServiceLayer(Layer.MESH);
        serviceRelation.setDestServiceName(metrics.getDestServiceName());
        serviceRelation.setDetectPoint(detectPointMapping(metrics.getDetectPoint()));
        serviceRelation.setComponentId(TCP_COMPONENT);
        serviceRelation.setTlsMode(metrics.getTlsMode());
        serviceRelation.getSideCar().setInternalErrorCode(metrics.getInternalErrorCode());
        serviceRelation.getSideCar().setInternalRequestLatencyNanos(metrics.getInternalRequestLatencyNanos());
        serviceRelation.getSideCar().setInternalResponseLatencyNanos(metrics.getInternalResponseLatencyNanos());
        serviceRelation.setReceivedBytes(metrics.getReceivedBytes());
        serviceRelation.setSentBytes(metrics.getSentBytes());

        SOURCE_RECEIVER.receive(serviceRelation);
    }

    private static void toEndpoint(HTTPServiceMeshMetric.Builder metrics, long minuteTimeBucket) {
        if (StringUtil.isEmpty(metrics.getEndpoint())) {
            return;
        }
        Endpoint endpoint = new Endpoint();
        endpoint.setTimeBucket(minuteTimeBucket);
        endpoint.setName(metrics.getEndpoint());
        endpoint.setServiceName(metrics.getDestServiceName());
        endpoint.setServiceLayer(Layer.MESH);
        endpoint.setServiceInstanceName(metrics.getDestServiceInstance());
        endpoint.setLatency(metrics.getLatency());
        endpoint.setStatus(metrics.getStatus());
        endpoint.setHttpResponseStatusCode(metrics.getResponseCode());
        endpoint.setType(protocol2Type(metrics.getProtocol()));
        endpoint.getSideCar().setInternalErrorCode(metrics.getInternalErrorCode());
        endpoint.getSideCar().setInternalRequestLatencyNanos(metrics.getInternalRequestLatencyNanos());
        endpoint.getSideCar().setInternalResponseLatencyNanos(metrics.getInternalResponseLatencyNanos());

        SOURCE_RECEIVER.receive(endpoint);
    }

    private static RequestType protocol2Type(Protocol protocol) {
        switch (protocol) {
            case gRPC:
                return RequestType.gRPC;
            case HTTP:
                return RequestType.HTTP;
            case UNRECOGNIZED:
            default:
                return RequestType.RPC;
        }
    }

    private static int protocol2Component(Protocol protocol) {
        switch (protocol) {
            case gRPC:
                // GRPC in component-libraries.yml
                return 23;
            case HTTP:
                // HTTP in component-libraries.yml
                return 49;
            case UNRECOGNIZED:
            default:
                // RPC in component-libraries.yml
                return 50;
        }
    }

    private static DetectPoint detectPointMapping(org.apache.skywalking.apm.network.common.v3.DetectPoint detectPoint) {
        switch (detectPoint) {
            case client:
                return DetectPoint.CLIENT;
            case proxy:
                return DetectPoint.PROXY;
            default:
                return DetectPoint.SERVER;
        }
    }

}
