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

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;

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
    private int httpResponseStatusCode;
    @Getter
    @Setter
    private String rpcStatusCode;
    @Getter
    @Setter
    private RequestType type;
    @Getter
    @Setter
    private DetectPoint detectPoint;
    @Getter
    @Setter
    private Layer serviceLayer;
    @Getter
    @Setter
    private Layer childServiceLayer;

    @Override
    public void prepare() {
        serviceId = IDManager.ServiceID.buildId(serviceName, serviceLayer.isNormal());
        childServiceId = IDManager.ServiceID.buildId(childServiceName, childServiceLayer.isNormal());
    }

    @Override
    public String toJson() {
        final JsonObject obj = new JsonObject();
        obj.addProperty("scope", scope());
        obj.addProperty("entityId", getEntityId());
        obj.addProperty("timeBucket", getTimeBucket());
        obj.addProperty("endpoint", endpoint);
        obj.addProperty("serviceName", serviceName);
        obj.addProperty("serviceLayer", serviceLayer == null ? null : serviceLayer.name());
        obj.addProperty("serviceInstanceName", serviceInstanceName);
        obj.addProperty("childEndpoint", childEndpoint);
        obj.addProperty("childServiceName", childServiceName);
        obj.addProperty("childServiceLayer", childServiceLayer == null ? null : childServiceLayer.name());
        obj.addProperty("childServiceInstanceName", childServiceInstanceName);
        obj.addProperty("componentId", componentId);
        obj.addProperty("rpcLatency", rpcLatency);
        obj.addProperty("status", status);
        obj.addProperty("httpResponseStatusCode", httpResponseStatusCode);
        obj.addProperty("rpcStatusCode", rpcStatusCode);
        obj.addProperty("type", type == null ? null : type.name());
        obj.addProperty("detectPoint", detectPoint == null ? null : detectPoint.name());
        return obj.toString();
    }
}

