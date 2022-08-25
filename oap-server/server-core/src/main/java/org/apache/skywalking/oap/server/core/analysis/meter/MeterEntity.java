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
import java.util.Map;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.DetectPoint;

/**
 * MeterEntity represents the entity in the meter system.
 */
@EqualsAndHashCode
@ToString
@Getter
public class MeterEntity {
    private static NamingControl NAMING_CONTROL;

    private ScopeType scopeType;
    private String serviceName;
    private String instanceName;
    private Map<String, String> instanceProperties;
    private String endpointName;
    private String sourceServiceName;
    private String destServiceName;
    private String sourceProcessId;
    private String destProcessId;
    private DetectPoint detectPoint;
    private Layer layer;
    private int componentId;

    private MeterEntity() {

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
            case SERVICE_RELATION:
                return IDManager.ServiceID.buildRelationId(new IDManager.ServiceID.ServiceRelationDefine(
                    sourceServiceId(),
                    destServiceId()
                ));
            case PROCESS_RELATION:
                return IDManager.ProcessID.buildRelationId(new IDManager.ProcessID.ProcessRelationDefine(
                    sourceProcessId,
                    destProcessId
                ));
            default:
                throw new UnexpectedException("Unexpected scope type of entity " + this.toString());
        }
    }

    public String serviceId() {
        return IDManager.ServiceID.buildId(serviceName, true);
    }

    public String serviceInstanceId() {
        return IDManager.ServiceInstanceID.buildId(serviceId(), instanceName);
    }

    public String sourceServiceId() {
        return IDManager.ServiceID.buildId(sourceServiceName, true);
    }

    public String destServiceId() {
        return IDManager.ServiceID.buildId(destServiceName, true);
    }

    public static void setNamingControl(final NamingControl namingControl) {
        NAMING_CONTROL = namingControl;
    }

    public void setServiceName(final String serviceName) {
        this.serviceName = NAMING_CONTROL.formatServiceName(serviceName);
    }

    /**
     * Create a service level meter entity.
     */
    public static MeterEntity newService(String serviceName, Layer layer) {
        final MeterEntity meterEntity = new MeterEntity();
        meterEntity.scopeType = ScopeType.SERVICE;
        meterEntity.serviceName = NAMING_CONTROL.formatServiceName(serviceName);
        meterEntity.layer = layer;
        return meterEntity;
    }

    /**
     * Create a service instance level meter entity.
     */
    public static MeterEntity newServiceInstance(String serviceName, String serviceInstance, Layer layer, Map<String, String> properties) {
        final MeterEntity meterEntity = new MeterEntity();
        meterEntity.scopeType = ScopeType.SERVICE_INSTANCE;
        meterEntity.serviceName = NAMING_CONTROL.formatServiceName(serviceName);
        meterEntity.instanceName = NAMING_CONTROL.formatInstanceName(serviceInstance);
        meterEntity.instanceProperties = properties;
        meterEntity.layer = layer;
        return meterEntity;
    }

    /**
     * Create an endpoint level meter entity.
     */
    public static MeterEntity newEndpoint(String serviceName, String endpointName, Layer layer) {
        final MeterEntity meterEntity = new MeterEntity();
        meterEntity.scopeType = ScopeType.ENDPOINT;
        meterEntity.serviceName = NAMING_CONTROL.formatServiceName(serviceName);
        meterEntity.endpointName = NAMING_CONTROL.formatEndpointName(serviceName, endpointName);
        meterEntity.layer = layer;
        return meterEntity;
    }

    public static MeterEntity newServiceRelation(String sourceServiceName,
                                                 String destServiceName,
                                                 DetectPoint detectPoint, Layer layer) {
        final MeterEntity meterEntity = new MeterEntity();
        meterEntity.scopeType = ScopeType.SERVICE_RELATION;
        meterEntity.sourceServiceName = NAMING_CONTROL.formatServiceName(sourceServiceName);
        meterEntity.destServiceName = NAMING_CONTROL.formatServiceName(destServiceName);
        meterEntity.detectPoint = detectPoint;
        meterEntity.layer = layer;
        return meterEntity;
    }

    public static MeterEntity newProcessRelation(String serviceName, String instanceName,
                                                 String sourceProcessId, String destProcessId,
                                                 int componentId, DetectPoint detectPoint) {
        final MeterEntity meterEntity = new MeterEntity();
        meterEntity.scopeType = ScopeType.PROCESS_RELATION;
        meterEntity.serviceName = serviceName;
        meterEntity.instanceName = instanceName;
        meterEntity.sourceProcessId = sourceProcessId;
        meterEntity.destProcessId = destProcessId;
        meterEntity.componentId = componentId;
        meterEntity.detectPoint = detectPoint;
        return meterEntity;
    }
}
