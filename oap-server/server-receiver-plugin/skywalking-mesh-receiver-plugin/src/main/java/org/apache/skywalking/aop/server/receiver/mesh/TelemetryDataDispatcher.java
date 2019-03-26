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
        StringFormatGroup.FormatResult formatResult = EndpointNameFormater.format(service, data.getEndpoint());
        if (formatResult.isMatch()) {
            data = data.toBuilder().setEndpoint(formatResult.getName()).build();
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
        ServiceMeshMetric metric = decorator.getMetric();
        long minuteTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(metric.getStartTime());

        heartbeat(decorator, minuteTimeBucket);
        if (org.apache.skywalking.apm.network.common.DetectPoint.server.equals(metric.getDetectPoint())) {
            toAll(decorator, minuteTimeBucket);
            toService(decorator, minuteTimeBucket);
            toServiceInstance(decorator, minuteTimeBucket);
            toEndpoint(decorator, minuteTimeBucket);
        }
        toServiceRelation(decorator, minuteTimeBucket);
        toServiceInstanceRelation(decorator, minuteTimeBucket);
    }

    private static void heartbeat(ServiceMeshMetricDataDecorator decorator, long minuteTimeBucket) {
        ServiceMeshMetric metric = decorator.getMetric();

        int heartbeatCycle = 10000;
        // source
        int instanceId = metric.getSourceServiceInstanceId();
        ServiceInstanceInventory serviceInstanceInventory = SERVICE_INSTANCE_CACHE.get(instanceId);
        if (Objects.nonNull(serviceInstanceInventory)) {
            if (metric.getEndTime() - serviceInstanceInventory.getHeartbeatTime() > heartbeatCycle) {
                // trigger heartbeat every 10s.
                SERVICE_INSTANCE_INVENTORY_REGISTER.heartbeat(metric.getSourceServiceInstanceId(), metric.getEndTime());
                SERVICE_INVENTORY_REGISTER.heartbeat(serviceInstanceInventory.getServiceId(), metric.getEndTime());
            }
        } else {
            logger.warn("Can't found service by service instance id from cache, service instance id is: {}", instanceId);
        }

        // dest
        instanceId = metric.getDestServiceInstanceId();
        serviceInstanceInventory = SERVICE_INSTANCE_CACHE.get(instanceId);
        if (Objects.nonNull(serviceInstanceInventory)) {
            if (metric.getEndTime() - serviceInstanceInventory.getHeartbeatTime() > heartbeatCycle) {
                // trigger heartbeat every 10s.
                SERVICE_INSTANCE_INVENTORY_REGISTER.heartbeat(metric.getDestServiceInstanceId(), metric.getEndTime());
                SERVICE_INVENTORY_REGISTER.heartbeat(serviceInstanceInventory.getServiceId(), metric.getEndTime());
            }
        } else {
            logger.warn("Can't found service by service instance id from cache, service instance id is: {}", instanceId);
        }
    }

    private static void toAll(ServiceMeshMetricDataDecorator decorator, long minuteTimeBucket) {
        ServiceMeshMetric metric = decorator.getMetric();
        All all = new All();
        all.setTimeBucket(minuteTimeBucket);
        all.setName(getServiceName(metric.getDestServiceId(), metric.getDestServiceName()));
        all.setServiceInstanceName(getServiceInstanceName(metric.getDestServiceInstanceId(), metric.getDestServiceInstance()));
        all.setEndpointName(metric.getEndpoint());
        all.setLatency(metric.getLatency());
        all.setStatus(metric.getStatus());
        all.setType(protocol2Type(metric.getProtocol()));

        SOURCE_RECEIVER.receive(all);
    }

    private static void toService(ServiceMeshMetricDataDecorator decorator, long minuteTimeBucket) {
        ServiceMeshMetric metric = decorator.getMetric();
        Service service = new Service();
        service.setTimeBucket(minuteTimeBucket);
        service.setId(metric.getDestServiceId());
        service.setName(getServiceName(metric.getDestServiceId(), metric.getDestServiceName()));
        service.setServiceInstanceName(getServiceInstanceName(metric.getDestServiceInstanceId(), metric.getDestServiceInstance()));
        service.setEndpointName(metric.getEndpoint());
        service.setLatency(metric.getLatency());
        service.setStatus(metric.getStatus());
        service.setType(protocol2Type(metric.getProtocol()));

        SOURCE_RECEIVER.receive(service);
    }

    private static void toServiceRelation(ServiceMeshMetricDataDecorator decorator, long minuteTimeBucket) {
        ServiceMeshMetric metric = decorator.getMetric();
        ServiceRelation serviceRelation = new ServiceRelation();
        serviceRelation.setTimeBucket(minuteTimeBucket);
        serviceRelation.setSourceServiceId(metric.getSourceServiceId());
        serviceRelation.setSourceServiceName(getServiceName(metric.getSourceServiceId(), metric.getSourceServiceName()));
        serviceRelation.setSourceServiceInstanceName(getServiceInstanceName(metric.getSourceServiceInstanceId(), metric.getSourceServiceInstance()));

        serviceRelation.setDestServiceId(metric.getDestServiceId());
        serviceRelation.setDestServiceName(getServiceName(metric.getDestServiceId(), metric.getDestServiceName()));
        serviceRelation.setDestServiceInstanceName(getServiceInstanceName(metric.getDestServiceInstanceId(), metric.getDestServiceInstance()));

        serviceRelation.setEndpoint(metric.getEndpoint());
        serviceRelation.setLatency(metric.getLatency());
        serviceRelation.setStatus(metric.getStatus());
        serviceRelation.setType(protocol2Type(metric.getProtocol()));
        serviceRelation.setResponseCode(metric.getResponseCode());
        serviceRelation.setDetectPoint(detectPointMapping(metric.getDetectPoint()));
        serviceRelation.setComponentId(protocol2Component(metric.getProtocol()));

        SOURCE_RECEIVER.receive(serviceRelation);
    }

    private static void toServiceInstance(ServiceMeshMetricDataDecorator decorator, long minuteTimeBucket) {
        ServiceMeshMetric metric = decorator.getMetric();
        ServiceInstance serviceInstance = new ServiceInstance();
        serviceInstance.setTimeBucket(minuteTimeBucket);
        serviceInstance.setId(metric.getDestServiceInstanceId());
        serviceInstance.setName(getServiceInstanceName(metric.getDestServiceInstanceId(), metric.getDestServiceInstance()));
        serviceInstance.setServiceId(metric.getDestServiceId());
        serviceInstance.setServiceName(getServiceName(metric.getDestServiceId(), metric.getDestServiceName()));
        serviceInstance.setEndpointName(metric.getEndpoint());
        serviceInstance.setLatency(metric.getLatency());
        serviceInstance.setStatus(metric.getStatus());
        serviceInstance.setType(protocol2Type(metric.getProtocol()));

        SOURCE_RECEIVER.receive(serviceInstance);
    }

    private static void toServiceInstanceRelation(ServiceMeshMetricDataDecorator decorator, long minuteTimeBucket) {
        ServiceMeshMetric metric = decorator.getMetric();
        ServiceInstanceRelation serviceRelation = new ServiceInstanceRelation();
        serviceRelation.setTimeBucket(minuteTimeBucket);
        serviceRelation.setSourceServiceInstanceId(metric.getSourceServiceInstanceId());
        serviceRelation.setSourceServiceInstanceName(getServiceInstanceName(metric.getSourceServiceInstanceId(), metric.getSourceServiceInstance()));
        serviceRelation.setSourceServiceId(metric.getSourceServiceId());
        serviceRelation.setSourceServiceName(getServiceName(metric.getSourceServiceId(), metric.getSourceServiceName()));

        serviceRelation.setDestServiceInstanceId(metric.getDestServiceInstanceId());
        serviceRelation.setDestServiceInstanceName(getServiceInstanceName(metric.getDestServiceInstanceId(), metric.getDestServiceInstance()));
        serviceRelation.setDestServiceId(metric.getDestServiceId());
        serviceRelation.setDestServiceName(getServiceName(metric.getDestServiceId(), metric.getDestServiceName()));

        serviceRelation.setEndpoint(metric.getEndpoint());
        serviceRelation.setLatency(metric.getLatency());
        serviceRelation.setStatus(metric.getStatus());
        serviceRelation.setType(protocol2Type(metric.getProtocol()));
        serviceRelation.setResponseCode(metric.getResponseCode());
        serviceRelation.setDetectPoint(detectPointMapping(metric.getDetectPoint()));
        serviceRelation.setComponentId(protocol2Component(metric.getProtocol()));

        SOURCE_RECEIVER.receive(serviceRelation);
    }

    private static void toEndpoint(ServiceMeshMetricDataDecorator decorator, long minuteTimeBucket) {
        ServiceMeshMetric metric = decorator.getMetric();
        Endpoint endpoint = new Endpoint();
        endpoint.setTimeBucket(minuteTimeBucket);
        endpoint.setId(decorator.getEndpointId());
        endpoint.setName(metric.getEndpoint());
        endpoint.setServiceId(metric.getDestServiceId());
        endpoint.setServiceName(getServiceName(metric.getDestServiceId(), metric.getDestServiceName()));
        endpoint.setServiceInstanceId(metric.getDestServiceInstanceId());
        endpoint.setServiceInstanceName(getServiceInstanceName(metric.getDestServiceInstanceId(), metric.getDestServiceInstance()));

        endpoint.setLatency(metric.getLatency());
        endpoint.setStatus(metric.getStatus());
        endpoint.setType(protocol2Type(metric.getProtocol()));

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
