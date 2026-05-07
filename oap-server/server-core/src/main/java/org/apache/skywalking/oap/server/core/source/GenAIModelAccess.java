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

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.GEN_AI_MODEL_ACCESS;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE_INSTANCE_CATALOG_NAME;

@Data
@ScopeDeclaration(id = GEN_AI_MODEL_ACCESS, name = "GenAIModelAccess", catalog = SERVICE_INSTANCE_CATALOG_NAME)
@ScopeDefaultColumn.VirtualColumnDefinition(fieldName = "entityId", columnName = "entity_id", isID = true, type = String.class)
public class GenAIModelAccess extends Source {

    @Override
    public int scope() {
        return GEN_AI_MODEL_ACCESS;
    }

    @Override
    public String getEntityId() {
        return entityId;
    }

    private String entityId;

    @ScopeDefaultColumn.DefinedByField(columnName = "service_name", requireDynamicActive = true)
    private String serviceName;

    @ScopeDefaultColumn.DefinedByField(columnName = "service_id")
    private String serviceId;

    @ScopeDefaultColumn.DefinedByField(columnName = "name")
    private String modelName;

    private long inputTokens;

    private long outputTokens;

    private long totalEstimatedCost;

    private int timeToFirstToken;

    private long latency;

    private boolean status;

    @Override
    public void prepare() {
        serviceId = IDManager.ServiceID.buildId(serviceName, Layer.VIRTUAL_GENAI.isNormal());
        entityId = IDManager.ServiceInstanceID.buildId(serviceId, modelName);
    }

    @Override
    public String toJson() {
        final JsonObject obj = new JsonObject();
        obj.addProperty("scope", scope());
        obj.addProperty("entityId", getEntityId());
        obj.addProperty("timeBucket", getTimeBucket());
        obj.addProperty("serviceName", serviceName);
        obj.addProperty("serviceId", serviceId);
        obj.addProperty("modelName", modelName);
        obj.addProperty("inputTokens", inputTokens);
        obj.addProperty("outputTokens", outputTokens);
        obj.addProperty("totalEstimatedCost", totalEstimatedCost);
        obj.addProperty("timeToFirstToken", timeToFirstToken);
        obj.addProperty("latency", latency);
        obj.addProperty("status", status);
        return obj.toString();
    }
}
