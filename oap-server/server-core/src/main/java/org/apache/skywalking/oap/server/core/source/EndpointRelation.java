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

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.NodeType;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.ENDPOINT_RELATION;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.ENDPOINT_RELATION_CATALOG_NAME;

@ScopeDeclaration(id = ENDPOINT_RELATION, name = "EndpointRelation", catalog = ENDPOINT_RELATION_CATALOG_NAME)
@ScopeDefaultColumn.VirtualColumnDefinition(fieldName = "entityId", columnName = "entity_id", isID = true, type = String.class)
public class EndpointRelation extends Source {

    @Override
    public int scope() {
        return DefaultScopeDefine.ENDPOINT_RELATION;
    }

    @Override
    public String getEntityId() {
        return IDManager.EndpointID.buildRelationId(new IDManager.EndpointID.EndpointRelationDefine(
            serviceId, endpoint, childServiceId, childEndpoint
        ));
    }

    @Getter
    @Setter
    @ScopeDefaultColumn.DefinedByField(columnName = "source_endpoint_name", requireDynamicActive = true)
    private String endpoint;
    @Getter
    private String serviceId;
    @Getter
    @Setter
    @ScopeDefaultColumn.DefinedByField(columnName = "source_service_name", requireDynamicActive = true)
    private String serviceName;
    @Setter
    private NodeType serviceNodeType;
    @Getter
    @Setter
    private String serviceInstanceName;
    @Getter
    @Setter
    @ScopeDefaultColumn.DefinedByField(columnName = "child_endpoint_name", requireDynamicActive = true)
    private String childEndpoint;
    @Getter
    private String childServiceId;
    @Setter
    @Getter
    @ScopeDefaultColumn.DefinedByField(columnName = "child_service_name", requireDynamicActive = true)
    private String childServiceName;
    @Setter
    private NodeType childServiceNodeType;
    @Getter
    @Setter
    private String childServiceInstanceName;
    @Getter
    @Setter
    private int componentId;
    @Getter
    @Setter
    private int rpcLatency;
    @Getter
    @Setter
    private boolean status;
    @Getter
    @Setter
    private int responseCode;
    @Getter
    @Setter
    private RequestType type;
    @Getter
    @Setter
    private DetectPoint detectPoint;

    @Override
    public void prepare() {
        serviceId = IDManager.ServiceID.buildId(serviceName, serviceNodeType);
        childServiceId = IDManager.ServiceID.buildId(childServiceName, childServiceNodeType);
    }
}

