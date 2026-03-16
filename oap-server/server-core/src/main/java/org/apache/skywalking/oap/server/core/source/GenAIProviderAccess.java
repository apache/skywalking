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

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.GEN_AI_PROVIDER_ACCESS;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE_CATALOG_NAME;

@Data
@ScopeDeclaration(id = GEN_AI_PROVIDER_ACCESS, name = "GenAIProviderAccess", catalog = SERVICE_CATALOG_NAME)
@ScopeDefaultColumn.VirtualColumnDefinition(fieldName = "entityId", columnName = "entity_id", isID = true, type = String.class)
public class GenAIProviderAccess extends Source {

    @Override
    public int scope() {
        return GEN_AI_PROVIDER_ACCESS;
    }

    @Override
    public String getEntityId() {
        return entityId;
    }

    private String entityId;

    @ScopeDefaultColumn.DefinedByField(columnName = "name", requireDynamicActive = true)
    private String name;

    private long inputTokens;

    private long outputTokens;

    private double totalCost;

    private long latency;

    private boolean status;

    @Override
    public void prepare() {
        entityId = IDManager.ServiceID.buildId(name, Layer.VIRTUAL_GENAI.isNormal());
    }
}
