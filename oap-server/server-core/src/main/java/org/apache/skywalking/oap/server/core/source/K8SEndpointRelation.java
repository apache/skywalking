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

package org.apache.skywalking.oap.server.core.source;

import lombok.Data;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.ENDPOINT_RELATION_CATALOG_NAME;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.K8S_ENDPOINT_REALATION;

@Data
@ScopeDeclaration(id = K8S_ENDPOINT_REALATION, name = "K8SEndpointRelation", catalog = ENDPOINT_RELATION_CATALOG_NAME)
@ScopeDefaultColumn.VirtualColumnDefinition(fieldName = "entityId", columnName = "entity_id", isID = true, type = String.class)
public class K8SEndpointRelation extends K8SMetrics.ProtocolMetrics {
    private volatile String entityId;

    private String sourceServiceId;
    private String sourceServiceName;
    private Layer sourceLayer;
    private String sourceEndpointId;
    private String sourceEndpointName;

    private DetectPoint detectPoint;
    private int componentId;

    private String destServiceId;
    private String destServiceName;
    private Layer destLayer;
    private String destEndpointId;
    private String destEndpointName;

    @Override
    public int scope() {
        return K8S_ENDPOINT_REALATION;
    }

    @Override
    public String getEntityId() {
        return entityId;
    }

    @Override
    public void prepare() {
        sourceServiceId = IDManager.ServiceID.buildId(sourceServiceName, sourceLayer.isNormal());
        sourceEndpointId = IDManager.EndpointID.buildId(sourceServiceId, sourceEndpointName);
        destServiceId = IDManager.ServiceID.buildId(destServiceName, destLayer.isNormal());
        destEndpointId = IDManager.EndpointID.buildId(destServiceId, destEndpointName);

        entityId = IDManager.EndpointID.buildRelationId(new IDManager.EndpointID.EndpointRelationDefine(
            sourceServiceId, sourceEndpointName, destServiceId, destEndpointName
        ));
    }
}
