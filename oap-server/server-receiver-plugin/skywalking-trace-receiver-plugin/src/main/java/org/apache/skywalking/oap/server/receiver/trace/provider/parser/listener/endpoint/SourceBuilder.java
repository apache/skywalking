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

package org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.endpoint;

import lombok.*;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.source.*;

/**
 * @author peng-yongsheng
 */
class SourceBuilder {

    @Getter @Setter private int sourceServiceId;
    @Getter @Setter private String sourceServiceName;
    @Getter @Setter private int sourceServiceInstanceId;
    @Getter @Setter private String sourceServiceInstanceName;
    @Getter @Setter private int sourceEndpointId;
    @Getter @Setter private String sourceEndpointName;
    @Getter @Setter private int destServiceId;
    @Getter @Setter private String destServiceName;
    @Getter @Setter private int destServiceInstanceId;
    @Getter @Setter private String destServiceInstanceName;
    @Getter @Setter private int destEndpointId;
    @Getter @Setter private String destEndpointName;
    @Getter @Setter private int componentId;
    @Getter @Setter private int latency;
    @Getter @Setter private boolean status;
    @Getter @Setter private int responseCode;
    @Getter @Setter private RequestType type;
    @Getter @Setter private DetectPoint detectPoint;
    @Getter @Setter private long timeBucket;

    All toAll() {
        All all = new All();
        all.setName(destServiceName);
        all.setServiceInstanceName(destServiceInstanceName);
        all.setEndpointName(destEndpointName);
        all.setLatency(latency);
        all.setStatus(status);
        all.setResponseCode(responseCode);
        all.setType(type);
        all.setTimeBucket(timeBucket);
        return all;
    }

    Service toService() {
        Service service = new Service();
        service.setId(destServiceId);
        service.setName(destServiceName);
        service.setServiceInstanceName(destServiceInstanceName);
        service.setEndpointName(destEndpointName);
        service.setLatency(latency);
        service.setStatus(status);
        service.setResponseCode(responseCode);
        service.setType(type);
        service.setTimeBucket(timeBucket);
        return service;
    }

    ServiceRelation toServiceRelation() {
        ServiceRelation serviceRelation = new ServiceRelation();
        serviceRelation.setSourceServiceId(sourceServiceId);
        serviceRelation.setSourceServiceName(sourceServiceName);
        serviceRelation.setSourceServiceInstanceName(sourceServiceInstanceName);
        serviceRelation.setDestServiceId(destServiceId);
        serviceRelation.setDestServiceName(destServiceName);
        serviceRelation.setDestServiceInstanceName(destServiceInstanceName);
        serviceRelation.setEndpoint(destEndpointName);
        serviceRelation.setComponentId(componentId);
        serviceRelation.setLatency(latency);
        serviceRelation.setStatus(status);
        serviceRelation.setResponseCode(responseCode);
        serviceRelation.setType(type);
        serviceRelation.setDetectPoint(detectPoint);
        serviceRelation.setTimeBucket(timeBucket);
        return serviceRelation;
    }

    ServiceInstance toServiceInstance() {
        ServiceInstance serviceInstance = new ServiceInstance();
        serviceInstance.setId(destServiceInstanceId);
        serviceInstance.setServiceId(destServiceId);
        serviceInstance.setName(destServiceInstanceName);
        serviceInstance.setServiceName(destServiceName);
        serviceInstance.setEndpointName(destEndpointName);
        serviceInstance.setLatency(latency);
        serviceInstance.setStatus(status);
        serviceInstance.setResponseCode(responseCode);
        serviceInstance.setType(type);
        serviceInstance.setTimeBucket(timeBucket);
        return serviceInstance;
    }

    ServiceInstanceRelation toServiceInstanceRelation() {
        ServiceInstanceRelation serviceInstanceRelation = new ServiceInstanceRelation();
        serviceInstanceRelation.setSourceServiceInstanceId(sourceServiceInstanceId);
        serviceInstanceRelation.setSourceServiceId(sourceServiceId);
        serviceInstanceRelation.setSourceServiceName(sourceServiceName);
        serviceInstanceRelation.setSourceServiceInstanceName(sourceServiceInstanceName);
        serviceInstanceRelation.setSourceServiceId(destServiceId);
        serviceInstanceRelation.setDestServiceInstanceId(destServiceInstanceId);
        serviceInstanceRelation.setDestServiceName(destServiceName);
        serviceInstanceRelation.setDestServiceInstanceName(destServiceInstanceName);
        serviceInstanceRelation.setEndpoint(destEndpointName);
        serviceInstanceRelation.setComponentId(componentId);
        serviceInstanceRelation.setLatency(latency);
        serviceInstanceRelation.setStatus(status);
        serviceInstanceRelation.setResponseCode(responseCode);
        serviceInstanceRelation.setType(type);
        serviceInstanceRelation.setDetectPoint(detectPoint);
        serviceInstanceRelation.setTimeBucket(timeBucket);
        return serviceInstanceRelation;
    }

    Endpoint toEndpoint() {
        Endpoint endpoint = new Endpoint();
        endpoint.setId(destEndpointId);
        endpoint.setName(destEndpointName);
        endpoint.setServiceId(destServiceId);
        endpoint.setServiceName(destServiceName);
        endpoint.setServiceInstanceId(destServiceInstanceId);
        endpoint.setServiceInstanceName(destServiceInstanceName);
        endpoint.setLatency(latency);
        endpoint.setStatus(status);
        endpoint.setResponseCode(responseCode);
        endpoint.setType(type);
        endpoint.setTimeBucket(timeBucket);
        return endpoint;
    }

    EndpointRelation toEndpointRelation() {
        if (StringUtil.isEmpty(sourceEndpointName) || StringUtil.isEmpty(destEndpointName)) {
            return null;
        }
        EndpointRelation endpointRelation = new EndpointRelation();
        endpointRelation.setEndpointId(sourceEndpointId);
        endpointRelation.setEndpoint(sourceEndpointName);
        endpointRelation.setServiceId(sourceServiceId);
        endpointRelation.setServiceName(sourceServiceName);
        endpointRelation.setServiceInstanceId(sourceServiceInstanceId);
        endpointRelation.setServiceInstanceName(sourceServiceInstanceName);
        endpointRelation.setChildEndpointId(destEndpointId);
        endpointRelation.setChildEndpoint(destEndpointName);
        endpointRelation.setChildServiceId(destServiceId);
        endpointRelation.setChildServiceName(destServiceName);
        endpointRelation.setChildServiceInstanceId(destServiceInstanceId);
        endpointRelation.setChildServiceInstanceName(destServiceInstanceName);
        endpointRelation.setComponentId(componentId);
        endpointRelation.setRpcLatency(latency);
        endpointRelation.setStatus(status);
        endpointRelation.setResponseCode(responseCode);
        endpointRelation.setType(type);
        endpointRelation.setDetectPoint(detectPoint);
        endpointRelation.setTimeBucket(timeBucket);
        return endpointRelation;
    }
}
