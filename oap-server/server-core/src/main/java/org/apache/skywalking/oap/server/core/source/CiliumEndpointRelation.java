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
import lombok.Data;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.ENDPOINT_RELATION_CATALOG_NAME;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.CILIUM_ENDPOINT_REALATION;

@Data
@ScopeDeclaration(id = CILIUM_ENDPOINT_REALATION, name = "CiliumEndpointRelation", catalog = ENDPOINT_RELATION_CATALOG_NAME)
@ScopeDefaultColumn.VirtualColumnDefinition(fieldName = "entityId", columnName = "entity_id", isID = true, type = String.class)
public class CiliumEndpointRelation extends CiliumMetrics {
    private volatile String entityId;

    private String sourceServiceId;
    private String sourceServiceName;
    private String sourceEndpointId;
    private String sourceEndpointName;
    private Layer sourceLayer;

    private DetectPoint detectPoint;
    private int componentId;

    private String destServiceId;
    private String destServiceName;
    private String destEndpointId;
    private String destEndpointName;
    private Layer destLayer;

    private boolean success;
    private long duration;

    @Override
    public int scope() {
        return CILIUM_ENDPOINT_REALATION;
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

    @Override
    public String toJson() {
        final JsonObject obj = new JsonObject();
        obj.addProperty("scope", scope());
        obj.addProperty("entityId", getEntityId());
        obj.addProperty("timeBucket", getTimeBucket());
        obj.addProperty("sourceServiceName", sourceServiceName);
        obj.addProperty("sourceEndpointName", sourceEndpointName);
        obj.addProperty("sourceLayer", sourceLayer == null ? null : sourceLayer.name());
        obj.addProperty("destServiceName", destServiceName);
        obj.addProperty("destEndpointName", destEndpointName);
        obj.addProperty("destLayer", destLayer == null ? null : destLayer.name());
        obj.addProperty("detectPoint", detectPoint == null ? null : detectPoint.name());
        obj.addProperty("componentId", componentId);
        obj.addProperty("verdict", getVerdict());
        obj.addProperty("type", getType());
        obj.addProperty("direction", getDirection());
        obj.addProperty("success", success);
        obj.addProperty("duration", duration);
        return obj.toString();
    }
}
