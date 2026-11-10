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

package org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.EndpointRelation;
import org.apache.skywalking.oap.server.core.source.Service;
import org.apache.skywalking.oap.server.core.source.ServiceInstance;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceRelation;
import org.apache.skywalking.oap.server.core.source.ServiceRelation;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * @since 9.0.0 Rename "SourceBuilder` to this. Add {@link EndpointSourceBuilder} for making source builder more specific.
 *
 * RPC traffic could be detected by server side or client side according to agent tech stack.
 */
class RPCTrafficSourceBuilder extends EndpointSourceBuilder {
    @Getter
    @Setter
    private String sourceServiceName;
    @Getter
    @Setter
    private Layer sourceLayer;
    @Getter
    @Setter
    private String sourceServiceInstanceName;
    /**
     * Same as {@link #sourceEndpointOwnerServiceName}
     * Source endpoint could be not owned by {@link #sourceServiceName}, such as in the MQ or un-instrumented proxy
     * cases. This service always comes from the span.ref, so it is always a general service.
     *
     * @since 9.0.0
     */
    @Getter
    @Setter
    private Layer sourceEndpointOwnerServiceLayer;
    /**
     * Source endpoint could be not owned by {@link #sourceServiceName}, such as in the MQ or un-instrumented proxy
     * cases. This service always comes from the span.ref, so it is always a general service.
     */
    @Getter
    @Setter
    private String sourceEndpointOwnerServiceName;
    @Getter
    @Setter
    private String sourceEndpointName;
    @Getter
    @Setter
    private int componentId;

    RPCTrafficSourceBuilder(final NamingControl namingControl) {
        super(namingControl);
    }

    void prepare() {
        this.sourceServiceName = namingControl.formatServiceName(sourceServiceName);
        this.sourceEndpointOwnerServiceName = namingControl.formatServiceName(sourceEndpointOwnerServiceName);
        this.sourceServiceInstanceName = namingControl.formatInstanceName(sourceServiceInstanceName);
        this.sourceEndpointName = namingControl.formatEndpointName(sourceServiceName, sourceEndpointName);
        super.prepare();
    }

    /**
     * Service meta and metrics related source of {@link #destServiceName}. The metrics base on the OAL scripts.
     */
    Service toService() {
        Service service = new Service();
        service.setName(destServiceName);
        service.setServiceInstanceName(destServiceInstanceName);
        service.setEndpointName(destEndpointName);
        service.setLayer(destLayer);
        service.setLatency(latency);
        service.setStatus(status);
        service.setHttpResponseStatusCode(httpResponseStatusCode);
        service.setRpcStatusCode(rpcStatusCode);
        service.setType(type);
        service.setTags(tags);
        service.setTimeBucket(timeBucket);
        service.setOriginalTags(originalTags);
        return service;
    }

    /**
     * Service topology meta and metrics related source. The metrics base on the OAL scripts.
     */
    ServiceRelation toServiceRelation() {
        ServiceRelation serviceRelation = new ServiceRelation();
        serviceRelation.setSourceServiceName(sourceServiceName);
        serviceRelation.setSourceServiceInstanceName(sourceServiceInstanceName);
        serviceRelation.setSourceLayer(sourceLayer);
        serviceRelation.setDestServiceName(destServiceName);
        serviceRelation.setDestServiceInstanceName(destServiceInstanceName);
        serviceRelation.setDestLayer(destLayer);
        serviceRelation.setEndpoint(destEndpointName);
        serviceRelation.setComponentId(componentId);
        serviceRelation.setLatency(latency);
        serviceRelation.setStatus(status);
        serviceRelation.setHttpResponseStatusCode(httpResponseStatusCode);
        serviceRelation.setRpcStatusCode(rpcStatusCode);
        serviceRelation.setType(type);
        serviceRelation.setDetectPoint(detectPoint);
        serviceRelation.setTimeBucket(timeBucket);
        return serviceRelation;
    }

    /**
     * Service instance meta and metrics of {@link #destServiceInstanceName} related source. The metrics base on the OAL
     * scripts.
     */
    ServiceInstance toServiceInstance() {
        ServiceInstance serviceInstance = new ServiceInstance();
        serviceInstance.setName(destServiceInstanceName);
        serviceInstance.setServiceName(destServiceName);
        serviceInstance.setServiceLayer(destLayer);
        serviceInstance.setEndpointName(destEndpointName);
        serviceInstance.setLatency(latency);
        serviceInstance.setStatus(status);
        serviceInstance.setHttpResponseStatusCode(httpResponseStatusCode);
        serviceInstance.setRpcStatusCode(rpcStatusCode);
        serviceInstance.setType(type);
        serviceInstance.setTags(tags);
        serviceInstance.setOriginalTags(originalTags);
        serviceInstance.setTimeBucket(timeBucket);
        return serviceInstance;
    }

    /**
     * Service instance topology/dependency meta and metrics related source. The metrics base on the OAL scripts.
     */
    ServiceInstanceRelation toServiceInstanceRelation() {
        if (StringUtil.isEmpty(sourceServiceInstanceName) || StringUtil.isEmpty(destServiceInstanceName)) {
            return null;
        }
        ServiceInstanceRelation serviceInstanceRelation = new ServiceInstanceRelation();
        serviceInstanceRelation.setSourceServiceName(sourceServiceName);
        serviceInstanceRelation.setSourceServiceInstanceName(sourceServiceInstanceName);
        serviceInstanceRelation.setSourceServiceLayer(sourceLayer);
        serviceInstanceRelation.setDestServiceName(destServiceName);
        serviceInstanceRelation.setDestServiceInstanceName(destServiceInstanceName);
        serviceInstanceRelation.setDestServiceLayer(destLayer);
        serviceInstanceRelation.setEndpoint(destEndpointName);
        serviceInstanceRelation.setComponentId(componentId);
        serviceInstanceRelation.setLatency(latency);
        serviceInstanceRelation.setStatus(status);
        serviceInstanceRelation.setHttpResponseStatusCode(httpResponseStatusCode);
        serviceInstanceRelation.setRpcStatusCode(rpcStatusCode);
        serviceInstanceRelation.setType(type);
        serviceInstanceRelation.setDetectPoint(detectPoint);
        serviceInstanceRelation.setTimeBucket(timeBucket);
        return serviceInstanceRelation;
    }

    /**
     * Endpoint dependency meta and metrics related source. The metrics base on the OAL scripts.
     */
    EndpointRelation toEndpointRelation() {
        if (StringUtil.isEmpty(sourceEndpointName) || StringUtil.isEmpty(destEndpointName)) {
            return null;
        }
        EndpointRelation endpointRelation = new EndpointRelation();
        endpointRelation.setEndpoint(sourceEndpointName);
        if (sourceEndpointOwnerServiceName == null) {
            endpointRelation.setServiceName(sourceServiceName);
            endpointRelation.setServiceLayer(sourceLayer);
        } else {
            endpointRelation.setServiceName(sourceEndpointOwnerServiceName);
            endpointRelation.setServiceLayer(sourceEndpointOwnerServiceLayer);
        }
        endpointRelation.setServiceInstanceName(sourceServiceInstanceName);
        endpointRelation.setChildEndpoint(destEndpointName);
        endpointRelation.setChildServiceName(destServiceName);
        endpointRelation.setChildServiceLayer(destLayer);
        endpointRelation.setChildServiceInstanceName(destServiceInstanceName);
        endpointRelation.setComponentId(componentId);
        endpointRelation.setRpcLatency(latency);
        endpointRelation.setStatus(status);
        endpointRelation.setHttpResponseStatusCode(httpResponseStatusCode);
        endpointRelation.setRpcStatusCode(rpcStatusCode);
        endpointRelation.setType(type);
        endpointRelation.setDetectPoint(detectPoint);
        endpointRelation.setTimeBucket(timeBucket);
        return endpointRelation;
    }
}
