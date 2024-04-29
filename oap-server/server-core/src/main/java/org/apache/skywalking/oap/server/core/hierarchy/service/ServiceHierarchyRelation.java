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

package org.apache.skywalking.oap.server.core.hierarchy.service;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.source.Source;

@ScopeDeclaration(id = DefaultScopeDefine.SERVICE_HIERARCHY_RELATION, name = "ServiceHierarchyRelation")
public class ServiceHierarchyRelation extends Source {
    /**
     * The service id of the upper service.
     */
    @Setter
    @Getter
    private String serviceName;
    /**
     * The service id of the upper service.
     */
    @Getter
    private String serviceId;
    /**
     * The service layer of the upper service.
     */
    @Setter
    @Getter
    private Layer serviceLayer;
    /**
     * The service name of the lower service.
     */
    @Setter
    @Getter
    private String relatedServiceName;
    /**
     * The service id of the lower service.
     */
    @Getter
    private String relatedServiceId;
    /**
     * The service layer of the lower service.
     */
    @Setter
    @Getter
    private Layer relatedServiceLayer;

    @Override
    public int scope() {
        return DefaultScopeDefine.SERVICE_HIERARCHY_RELATION;
    }

    @Override
    public String getEntityId() {
        return null;
    }

    @Override
    public void prepare() {
        serviceId = IDManager.ServiceID.buildId(serviceName, serviceLayer.isNormal());
        relatedServiceId = IDManager.ServiceID.buildId(relatedServiceName, relatedServiceLayer.isNormal());
    }
}
