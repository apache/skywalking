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
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.core.analysis.IDManager;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE_RELATION;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE_RELATION_CATALOG_NAME;

@ScopeDeclaration(id = SERVICE_RELATION, name = "ServiceRelation", catalog = SERVICE_RELATION_CATALOG_NAME)
@ScopeDefaultColumn.VirtualColumnDefinition(fieldName = "entityId", columnName = "entity_id", isID = true, type = String.class)
public class ServiceRelation extends Source {
    private String entityId;

    @Override
    public int scope() {
        return DefaultScopeDefine.SERVICE_RELATION;
    }

    @Override
    public String getEntityId() {
        if (StringUtil.isEmpty(entityId)) {
            entityId = IDManager.ServiceID.buildRelationId(
                new IDManager.ServiceID.ServiceRelationDefine(
                    sourceServiceId,
                    destServiceId
                )
            );
        }
        return entityId;
    }

    @Getter
    private String sourceServiceId;
    @Getter
    @Setter
    @ScopeDefaultColumn.DefinedByField(columnName = "source_name", requireDynamicActive = true)
    private String sourceServiceName;
    @Getter
    @Setter
    private String sourceServiceInstanceName;
    @Getter
    @Setter
    private Layer sourceLayer;
    @Getter
    private String destServiceId;
    @Getter
    @Setter
    @ScopeDefaultColumn.DefinedByField(columnName = "dest_name", requireDynamicActive = true)
    private String destServiceName;
    @Getter
    @Setter
    private Layer destLayer;
    @Getter
    @Setter
    private String destServiceInstanceName;
    @Getter
    @Setter
    private String endpoint;
    @Getter
    @Setter
    private int componentId;
    @Getter
    @Setter
    private int latency;
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
    private String tlsMode;
    @Getter
    @Setter
    private SideCar sideCar = new SideCar();

    @Override
    public void prepare() {
        sourceServiceId = IDManager.ServiceID.buildId(sourceServiceName, sourceLayer.isNormal());
        destServiceId = IDManager.ServiceID.buildId(destServiceName, destLayer.isNormal());
    }

    /**
     * Field-by-field JSON payload for the dsl-debugging capture. Built
     * via Gson's {@link JsonObject} — explicit field selection (no
     * reflection-based serialization) with library-handled string
     * escaping. Exposes the service-pair endpoints, per-side layer, and
     * the call attributes operators reach for when reading captured
     * filter / source samples.
     */
    @Override
    public String toJson() {
        final JsonObject obj = new JsonObject();
        obj.addProperty("scope", scope());
        obj.addProperty("entityId", getEntityId());
        obj.addProperty("timeBucket", getTimeBucket());
        obj.addProperty("sourceServiceName", sourceServiceName);
        obj.addProperty("destServiceName", destServiceName);
        obj.addProperty("sourceLayer", sourceLayer == null ? null : sourceLayer.name());
        obj.addProperty("destLayer", destLayer == null ? null : destLayer.name());
        obj.addProperty("detectPoint", detectPoint == null ? null : detectPoint.name());
        obj.addProperty("type", type == null ? null : type.name());
        obj.addProperty("endpoint", endpoint);
        obj.addProperty("componentId", componentId);
        obj.addProperty("latency", latency);
        obj.addProperty("status", status);
        obj.addProperty("httpResponseStatusCode", httpResponseStatusCode);
        return obj.toString();
    }
}
