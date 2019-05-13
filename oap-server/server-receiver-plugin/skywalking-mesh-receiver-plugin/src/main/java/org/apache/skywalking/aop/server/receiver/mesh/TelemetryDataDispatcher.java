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

import java.util.Objects;
import org.apache.logging.log4j.util.Strings;
import org.apache.skywalking.apm.network.servicemesh.*;
import org.apache.skywalking.apm.util.StringFormatGroup;
import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.cache.*;
import org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory;
import org.apache.skywalking.oap.server.core.register.service.*;
import org.apache.skywalking.oap.server.core.source.*;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.TimeBucketUtils;
import org.slf4j.*;

/**
 * TelemetryDataDispatcher processes the {@link ServiceMeshMetric} format telemetry data, transfers it to source
 * dispatcher.
 *
 * @author wusheng
 */
public class TelemetryDataDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(TelemetryDataDispatcher.class);

    private static MeshDataBufferFileCache CACHE;
    private static ServiceInventoryCache SERVICE_CACHE;
    private static ServiceInstanceInventoryCache SERVICE_INSTANCE_CACHE;
    private static SourceReceiver SOURCE_RECEIVER;
    private static IServiceInstanceInventoryRegister SERVICE_INSTANCE_INVENTORY_REGISTER;
    private static IServiceInventoryRegister SERVICE_INVENTORY_REGISTER;

    private TelemetryDataDispatcher() {

    }

    public static void setCache(MeshDataBufferFileCache cache, ModuleManager moduleManager) {
        CACHE = cache;
        SERVICE_CACHE = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInventoryCache.class);
        SERVICE_INSTANCE_CACHE = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInstanceInventoryCache.class);
        SOURCE_RECEIVER = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
        SERVICE_INSTANCE_INVENTORY_REGISTER = moduleManager.find(CoreModule.NAME).provider().getService(IServiceInstanceInventoryRegister.class);
        SERVICE_INVENTORY_REGISTER = moduleManager.find(CoreModule.NAME).provider().getService(IServiceInventoryRegister.class);
    }

    public static void preProcess(ServiceMeshMetric data) {
        String service = data.getDestServiceId() == Const.NONE ? data.getDestServiceName() :
            SERVICE_CACHE.get(data.getDestServiceId()).getName();
        String endpointName = data.getEndpoint();
        StringFormatGroup.FormatResult formatResult = EndpointNameFormater.format(service, endpointName);
        if (formatResult.isMatch()) {
            data = data.toBuilder().setEndpoint(formatResult.getName()).build();
        }
        if (logger.isDebugEnabled()) {
            if (formatResult.isMatch()) {
                logger.debug("Endpoint {} is renamed to {}", endpointName, data.getEndpoint());
            }
        }

        ServiceMeshMetricDataDecorator decorator = new ServiceMeshMetricDataDecorator(data);
        if (decorator.tryMetaDataRegister()) {
            TelemetryDataDispatcher.doDispatch(decorator);
        } else {
            CACHE.in(data);
        }
    }

    /**
     * The {@link ServiceMeshMetricDataDecorator} is standard, all metadata registered through {@link #CACHE}
     *
     * @param decorator
     */
    static void doDispatch(ServiceMeshMetricDataDecorator decorator) {
        ServiceMeshMetric metrics = decorator.getMetric();
        long minuteTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(metrics.getStartTime());

        heartbeat(decorator, minuteTimeBucket);
        if (org.apache.skywalking.apm.network.common.DetectPoint.server.equals(metrics.getDetectPoint())) {
            toAll(decorator, minuteTimeBucket);
            toService(decorator, minuteTimeBucket);
            toServiceInstance(decorator, minuteTimeBucket);
            toEndpoint(decorator, minuteTimeBucket);
        }
        toServiceRelation(decorator, minuteTimeBucket);
        toServiceInstanceRelation(decorator, minuteTimeBucket);
    }

    private static void heartbeat(ServiceMeshMetricDataDecorator decorator, long minuteTimeBucket) {
        ServiceMeshMetric metrics = decorator.getMetric();

        int heartbeatCycle = 10000;
        // source
        int instanceId = metrics.getSourceServiceInstanceId();
        ServiceInstanceInventory serviceInstanceInventory = SERVICE_INSTANCE_CACHE.get(instanceId);
        if (Objects.nonNull(serviceInstanceInventory)) {
            if (metrics.getEndTime() - serviceInstanceInventory.getHeartbeatTime() > heartbeatCycle) {
                // trigger heartbeat every 10s.
                SERVICE_INSTANCE_INVENTORY_REGISTER.heartbeat(metrics.getSourceServiceInstanceId(), metrics.getEndTime());
                SERVICE_INVENTORY_REGISTER.heartbeat(serviceInstanceInventory.getServiceId(), metrics.getEndTime());
            }
        } else {
            logger.warn("Can't found service by service instance id from cache, service instance id is: {}", instanceId);
        }

        // dest
        instanceId = metrics.getDestServiceInstanceId();
        serviceInstanceInventory = SERVICE_INSTANCE_CACHE.get(instanceId);
        if (Objects.nonNull(serviceInstanceInventory)) {
            if (metrics.getEndTime() - serviceInstanceInventory.getHeartbeatTime() > heartbeatCycle) {
                // trigger heartbeat every 10s.
                SERVICE_INSTANCE_INVENTORY_REGISTER.heartbeat(metrics.getDestServiceInstanceId(), metrics.getEndTime());
                SERVICE_INVENTORY_REGISTER.heartbeat(serviceInstanceInventory.getServiceId(), metrics.getEndTime());
            }
        } else {
            logger.warn("Can't found service by service instance id from cache, service instance id is: {}", instanceId);
        }
    }

    private static void toAll(ServiceMeshMetricDataDecorator decorator, long minuteTimeBucket) {
        ServiceMeshMetric metrics = decorator.getMetric();
        All all = new All();
        all.setTimeBucket(minuteTimeBucket);
        all.setName(getServiceName(metrics.getDestServiceId(), metrics.getDestServiceName()));
        all.setServiceInstanceName(getServiceInstanceName(metrics.getDestServiceInstanceId(), metrics.getDestServiceInstance()));
        all.setEndpointName(metrics.getEndpoint());
        all.setLatency(metrics.getLatency());
        all.setStatus(metrics.getStatus());
        all.setType(protocol2Type(metrics.getProtocol()));

        SOURCE_RECEIVER.receive(all);
    }

    private static void toService(ServiceMeshMetricDataDecorator decorator, long minuteTimeBucket) {
        ServiceMeshMetric metrics = decorator.getMetric();
        Service service = new Service();
        service.setTimeBucket(minuteTimeBucket);
        service.setId(metrics.getDestServiceId());
        service.setName(getServiceName(metrics.getDestServiceId(), metrics.getDestServiceName()));
        service.setServiceInstanceName(getServiceInstanceName(metrics.getDestServiceInstanceId(), metrics.getDestServiceInstance()));
        service.setEndpointName(metrics.getEndpoint());
        service.setLatency(metrics.getLatency());
        service.setStatus(metrics.getStatus());
        service.setType(protocol2Type(metrics.getProtocol()));

        SOURCE_RECEIVER.receive(service);
    }

    private static void toServiceRelation(ServiceMeshMetricDataDecorator decorator, long minuteTimeBucket) {
        ServiceMeshMetric metrics = decorator.getMetric();
        ServiceRelation serviceRelation = new ServiceRelation();
        serviceRelation.setTimeBucket(minuteTimeBucket);
        serviceRelation.setSourceServiceId(metrics.getSourceServiceId());
        serviceRelation.setSourceServiceName(getServiceName(metrics.getSourceServiceId(), metrics.getSourceServiceName()));
        serviceRelation.setSourceServiceInstanceName(getServiceInstanceName(metrics.getSourceServiceInstanceId(), metrics.getSourceServiceInstance()));

        serviceRelation.setDestServiceId(metrics.getDestServiceId());
        serviceRelation.setDestServiceName(getServiceName(metrics.getDestServiceId(), metrics.getDestServiceName()));
        serviceRelation.setDestServiceInstanceName(getServiceInstanceName(metrics.getDestServiceInstanceId(), metrics.getDestServiceInstance()));

        serviceRelation.setEndpoint(metrics.getEndpoint());
        serviceRelation.setLatency(metrics.getLatency());
        serviceRelation.setStatus(metrics.getStatus());
        serviceRelation.setType(protocol2Type(metrics.getProtocol()));
        serviceRelation.setResponseCode(metrics.getResponseCode());
        serviceRelation.setDetectPoint(detectPointMapping(metrics.getDetectPoint()));
        serviceRelation.setComponentId(protocol2Component(metrics.getProtocol()));

        SOURCE_RECEIVER.receive(serviceRelation);
    }

    private static void toServiceInstance(ServiceMeshMetricDataDecorator decorator, long minuteTimeBucket) {
        ServiceMeshMetric metrics = decorator.getMetric();
        ServiceInstance serviceInstance = new ServiceInstance();
        serviceInstance.setTimeBucket(minuteTimeBucket);
        serviceInstance.setId(metrics.getDestServiceInstanceId());
        serviceInstance.setName(getServiceInstanceName(metrics.getDestServiceInstanceId(), metrics.getDestServiceInstance()));
        serviceInstance.setServiceId(metrics.getDestServiceId());
        serviceInstance.setServiceName(getServiceName(metrics.getDestServiceId(), metrics.getDestServiceName()));
        serviceInstance.setEndpointName(metrics.getEndpoint());
        serviceInstance.setLatency(metrics.getLatency());
        serviceInstance.setStatus(metrics.getStatus());
        serviceInstance.setType(protocol2Type(metrics.getProtocol()));

        SOURCE_RECEIVER.receive(serviceInstance);
    }

    private static void toServiceInstanceRelation(ServiceMeshMetricDataDecorator decorator, long minuteTimeBucket) {
        ServiceMeshMetric metrics = decorator.getMetric();
        ServiceInstanceRelation serviceRelation = new ServiceInstanceRelation();
        serviceRelation.setTimeBucket(minuteTimeBucket);
        serviceRelation.setSourceServiceInstanceId(metrics.getSourceServiceInstanceId());
        serviceRelation.setSourceServiceInstanceName(getServiceInstanceName(metrics.getSourceServiceInstanceId(), metrics.getSourceServiceInstance()));
        serviceRelation.setSourceServiceId(metrics.getSourceServiceId());
        serviceRelation.setSourceServiceName(getServiceName(metrics.getSourceServiceId(), metrics.getSourceServiceName()));

        serviceRelation.setDestServiceInstanceId(metrics.getDestServiceInstanceId());
        serviceRelation.setDestServiceInstanceName(getServiceInstanceName(metrics.getDestServiceInstanceId(), metrics.getDestServiceInstance()));
        serviceRelation.setDestServiceId(metrics.getDestServiceId());
        serviceRelation.setDestServiceName(getServiceName(metrics.getDestServiceId(), metrics.getDestServiceName()));

        serviceRelation.setEndpoint(metrics.getEndpoint());
        serviceRelation.setLatency(metrics.getLatency());
        serviceRelation.setStatus(metrics.getStatus());
        serviceRelation.setType(protocol2Type(metrics.getProtocol()));
        serviceRelation.setResponseCode(metrics.getResponseCode());
        serviceRelation.setDetectPoint(detectPointMapping(metrics.getDetectPoint()));
        serviceRelation.setComponentId(protocol2Component(metrics.getProtocol()));

        SOURCE_RECEIVER.receive(serviceRelation);
    }

    private static void toEndpoint(ServiceMeshMetricDataDecorator decorator, long minuteTimeBucket) {
        ServiceMeshMetric metrics = decorator.getMetric();
        Endpoint endpoint = new Endpoint();
        endpoint.setTimeBucket(minuteTimeBucket);
        endpoint.setId(decorator.getEndpointId());
        endpoint.setName(metrics.getEndpoint());
        endpoint.setServiceId(metrics.getDestServiceId());
        endpoint.setServiceName(getServiceName(metrics.getDestServiceId(), metrics.getDestServiceName()));
        endpoint.setServiceInstanceId(metrics.getDestServiceInstanceId());
        endpoint.setServiceInstanceName(getServiceInstanceName(metrics.getDestServiceInstanceId(), metrics.getDestServiceInstance()));

        endpoint.setLatency(metrics.getLatency());
        endpoint.setStatus(metrics.getStatus());
        endpoint.setType(protocol2Type(metrics.getProtocol()));

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
                // GRPC in componentId-libraries.yml
                return 23;
            case HTTP:
                // HTTP in componentId-libraries.yml
                return 49;
            case UNRECOGNIZED:
            default:
                // RPC in componentId-libraries.yml
                return 50;
        }
    }

    private static DetectPoint detectPointMapping(org.apache.skywalking.apm.network.common.DetectPoint detectPoint) {
        switch (detectPoint) {
            case client:
                return DetectPoint.CLIENT;
            case server:
                return DetectPoint.SERVER;
            case proxy:
                return DetectPoint.PROXY;
            default:
                return DetectPoint.SERVER;
        }
    }

    private static String getServiceName(int serviceId, String serviceName) {
        if (Strings.isBlank(serviceName)) {
            return SERVICE_CACHE.get(serviceId).getName();
        } else {
            return serviceName;
        }
    }

    private static String getServiceInstanceName(int serviceInstanceId, String serviceInstanceName) {
        if (Strings.isBlank(serviceInstanceName)) {
            return SERVICE_INSTANCE_CACHE.get(serviceInstanceId).getName();
        } else {
            return serviceInstanceName;
        }
    }
}
