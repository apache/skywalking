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
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.manual.RelationDefineUtil;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.ENDPOINT_RELATION;

@ScopeDeclaration(id = ENDPOINT_RELATION, name = "EndpointRelation")
@ScopeDefaultColumn.VirtualColumnDefinition(fieldName = "entityId", columnName = "entity_id", isID = true, type = String.class)
public class EndpointRelation extends Source {

    @Override
    public int scope() {
        return DefaultScopeDefine.ENDPOINT_RELATION;
    }

    /**
     * @since 7.1.0 SkyWalking doesn't do endpoint register. Use name directly.
     */
    @Override
    public String getEntityId() {
        return RelationDefineUtil.buildEndpointRelationEntityId(new RelationDefineUtil.EndpointRelationDefine(
            serviceId, endpoint, childServiceId, childEndpoint, componentId
        ));
    }

    @Getter
    @ScopeDefaultColumn.DefinedByField(columnName = "source_endpoint_name")
    private String endpoint;

    public void setEndpoint(final String endpoint) {
        this.endpoint = CoreModule.formatEndpointName(endpoint);
    }

    @Getter
    @Setter
    @ScopeDefaultColumn.DefinedByField(columnName = "service_id")
    private int serviceId;
    @Getter
    @Setter
    @ScopeDefaultColumn.DefinedByField(columnName = "source_service_name", requireDynamicActive = true)
    private String serviceName;
    @Getter
    @Setter
    private int serviceInstanceId;
    @Getter
    @Setter
    private String serviceInstanceName;
    @Getter
    @ScopeDefaultColumn.DefinedByField(columnName = "child_endpoint_name")
    private String childEndpoint;

    public void setChildEndpoint(final String childEndpoint) {
        this.childEndpoint = CoreModule.formatEndpointName(childEndpoint);
    }

    @Getter
    @Setter
    @ScopeDefaultColumn.DefinedByField(columnName = "child_service_id")
    private int childServiceId;
    @Setter
    @Getter
    @ScopeDefaultColumn.DefinedByField(columnName = "child_service_name", requireDynamicActive = true)
    private String childServiceName;
    @Getter
    @Setter
    private int childServiceInstanceId;
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
}

