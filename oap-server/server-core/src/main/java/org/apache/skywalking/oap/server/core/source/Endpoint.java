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

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.ISourceDecorator;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.SourceDecoratorManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.core.analysis.IDManager;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.ENDPOINT;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.ENDPOINT_CATALOG_NAME;

@ScopeDeclaration(id = ENDPOINT, name = "Endpoint", catalog = ENDPOINT_CATALOG_NAME)
@ScopeDefaultColumn.VirtualColumnDefinition(fieldName = "entityId", columnName = "entity_id", isID = true, type = String.class)
@Slf4j
public class Endpoint extends Source {
    private String entityId;

    @Override
    public int scope() {
        return DefaultScopeDefine.ENDPOINT;
    }

    @Override
    public String getEntityId() {
        if (StringUtil.isEmpty(entityId)) {
            entityId = IDManager.EndpointID.buildId(serviceId, name);
        }
        return entityId;
    }

    @Getter
    @Setter
    @ScopeDefaultColumn.DefinedByField(columnName = "name", requireDynamicActive = true)
    private String name;
    @Getter
    @ScopeDefaultColumn.DefinedByField(columnName = "service_id")
    @ScopeDefaultColumn.BanyanDB(shardingKeyIdx = 0)
    private String serviceId;
    @Getter
    @Setter
    @ScopeDefaultColumn.DefinedByField(columnName = "service_name", requireDynamicActive = true)
    private String serviceName;
    @Getter
    @Setter
    private String serviceInstanceName;
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
    private List<String> tags;
    @Setter
    private Map<String, String> originalTags;
    @Getter
    @Setter
    private SideCar sideCar = new SideCar();
    @Getter
    @Setter
    private Layer serviceLayer;
    @Getter
    @Setter
    @ScopeDefaultColumn.DefinedByField(columnName = "attr0", isAttribute = true)
    private String attr0;
    @Getter
    @Setter
    @ScopeDefaultColumn.DefinedByField(columnName = "attr1", isAttribute = true)
    private String attr1;
    @Getter
    @Setter
    @ScopeDefaultColumn.DefinedByField(columnName = "attr2", isAttribute = true)
    private String attr2;
    @Getter
    @Setter
    @ScopeDefaultColumn.DefinedByField(columnName = "attr3", isAttribute = true)
    private String attr3;
    @Getter
    @Setter
    @ScopeDefaultColumn.DefinedByField(columnName = "attr4", isAttribute = true)
    private String attr4;
    @Getter
    @Setter
    @ScopeDefaultColumn.DefinedByField(columnName = "attr5", isAttribute = true)
    private String attr5;

    /**
     * Get the decorator through given name and invoke.
     * @param decorator The decorator class simpleName.
     */
    public void decorate(String decorator) {
        ISourceDecorator<ISource> sourceDecorator = SourceDecoratorManager.DECORATOR_MAP.get(decorator);
        sourceDecorator.decorate(this);
    }

    @Override
    public void prepare() {
        serviceId = IDManager.ServiceID.buildId(serviceName, serviceLayer.isNormal());
    }

    public String getTag(String key) {
        return originalTags.get(key);
    }
}
