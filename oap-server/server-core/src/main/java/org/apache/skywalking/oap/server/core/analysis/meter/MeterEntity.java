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

package org.apache.skywalking.oap.server.core.analysis.meter;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.IDManager;

/**
 * MeterEntity represents the entity in the meter system.
 */
@EqualsAndHashCode
@ToString
@Getter
public class MeterEntity {
    private ScopeType scopeType;
    private String serviceName;
    private String instanceName;
    private String endpointName;

    private MeterEntity(final ScopeType scopeType,
                        final String serviceName,
                        final String instanceName,
                        final String endpointName) {
        this.scopeType = scopeType;
        this.serviceName = serviceName;
        this.instanceName = instanceName;
        this.endpointName = endpointName;
    }

    public String id() {
        switch (scopeType) {
            case SERVICE:
                // In Meter system, only normal service, because we don't conjecture any node.
                return IDManager.ServiceID.buildId(serviceName, true);
            case SERVICE_INSTANCE:
                return IDManager.ServiceInstanceID.buildId(
                    IDManager.ServiceID.buildId(serviceName, true), instanceName);
            case ENDPOINT:
                return IDManager.EndpointID.buildId(IDManager.ServiceID.buildId(serviceName, true), endpointName);
            default:
                throw new UnexpectedException("Unexpected scope type of entity " + this.toString());
        }
    }

    public String serviceId() {
        return IDManager.ServiceID.buildId(serviceName, true);
    }

    /**
     * Create a service level meter entity.
     */
    public static MeterEntity newService(String serviceName) {
        return new MeterEntity(ScopeType.SERVICE, serviceName, null, null);
    }

    /**
     * Create a service instance level meter entity.
     */
    public static MeterEntity newServiceInstance(String serviceName, String serviceInstance) {
        return new MeterEntity(ScopeType.SERVICE_INSTANCE, serviceName, serviceInstance, null);
    }

    /**
     * Create an endpoint level meter entity.
     */
    public static MeterEntity newEndpoint(String serviceName, String endpointName) {
        return new MeterEntity(ScopeType.ENDPOINT, serviceName, null, endpointName);
    }
}
