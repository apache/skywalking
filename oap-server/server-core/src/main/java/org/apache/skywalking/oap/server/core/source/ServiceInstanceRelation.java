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
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.NodeType;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE_INSTANCE_RELATION;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE_INSTANCE_RELATION_CATALOG_NAME;

@ScopeDeclaration(id = SERVICE_INSTANCE_RELATION, name = "ServiceInstanceRelation", catalog = SERVICE_INSTANCE_RELATION_CATALOG_NAME)
@ScopeDefaultColumn.VirtualColumnDefinition(fieldName = "entityId", columnName = "entity_id", isID = true, type = String.class)
public class ServiceInstanceRelation extends Source {
    private String entityId;

    @Override
    public int scope() {
        return DefaultScopeDefine.SERVICE_INSTANCE_RELATION;
    }

    @Override
    public String getEntityId() {
        if (StringUtil.isEmpty(entityId)) {
            entityId = IDManager.ServiceInstanceID.buildRelationId(
                new IDManager.ServiceInstanceID.ServiceInstanceRelationDefine(
                    sourceServiceInstanceId,
                    destServiceInstanceId
                )
            );
        }
        return entityId;
    }

    @Getter
    private String sourceServiceInstanceId;
    @Getter
    private String sourceServiceId;
    @Getter
    @Setter
    @ScopeDefaultColumn.DefinedByField(columnName = "source_service_name", requireDynamicActive = true)
    private String sourceServiceName;
    @Setter
    private NodeType sourceServiceNodeType;
    @Getter
    @Setter
    @ScopeDefaultColumn.DefinedByField(columnName = "source_service_instance_name", requireDynamicActive = true)
    private String sourceServiceInstanceName;
    @Getter
    private String destServiceInstanceId;
    @Getter
    private String destServiceId;
    @Setter
    private NodeType destServiceNodeType;
    @Getter
    @Setter
    @ScopeDefaultColumn.DefinedByField(columnName = "dest_service_name", requireDynamicActive = true)
    private String destServiceName;
    @Getter
    @Setter
    @ScopeDefaultColumn.DefinedByField(columnName = "dest_service_instance_name", requireDynamicActive = true)
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
    private int responseCode;
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
        sourceServiceId = IDManager.ServiceID.buildId(sourceServiceName, sourceServiceNodeType);
        destServiceId = IDManager.ServiceID.buildId(destServiceName, destServiceNodeType);
        sourceServiceInstanceId = IDManager.ServiceInstanceID.buildId(sourceServiceId, sourceServiceInstanceName);
        destServiceInstanceId = IDManager.ServiceInstanceID.buildId(destServiceId, destServiceInstanceName);
    }
}
