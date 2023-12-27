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

package org.apache.skywalking.oap.server.core.hierarchy.instance;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.source.Source;

@ScopeDeclaration(id = DefaultScopeDefine.INSTANCE_HIERARCHY_RELATION, name = "InstanceHierarchyRelation")
public class InstanceHierarchyRelation extends Source {
    @Setter
    @Getter
    private String instanceName;
    @Getter
    private String instanceId;
    @Setter
    @Getter
    private String serviceName;
    @Getter
    private String serviceId;
    @Setter
    @Getter
    private Layer serviceLayer;
    @Setter
    @Getter
    private String relatedInstanceName;
    @Getter
    private String relatedInstanceId;
    @Setter
    @Getter
    private String relatedServiceName;
    @Getter
    private String relatedServiceId;
    @Setter
    @Getter
    private Layer relatedServiceLayer;

    @Override
    public int scope() {
        return DefaultScopeDefine.INSTANCE_HIERARCHY_RELATION;
    }

    @Override
    public String getEntityId() {
        return null;
    }

    @Override
    public void prepare() {
        serviceId = IDManager.ServiceID.buildId(serviceName, serviceLayer.isNormal());
        relatedServiceId = IDManager.ServiceID.buildId(relatedServiceName, relatedServiceLayer.isNormal());
        instanceId = IDManager.ServiceInstanceID.buildId(serviceId, instanceName);
        relatedInstanceId = IDManager.ServiceInstanceID.buildId(relatedServiceId, relatedInstanceName);
    }
}
