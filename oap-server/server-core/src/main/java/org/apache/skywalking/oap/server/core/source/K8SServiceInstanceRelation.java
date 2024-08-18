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

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.K8S_SERVICE_INSTANCE_RELATION;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE_INSTANCE_RELATION_CATALOG_NAME;

@Data
@ScopeDeclaration(id = K8S_SERVICE_INSTANCE_RELATION, name = "K8SServiceInstanceRelation", catalog = SERVICE_INSTANCE_RELATION_CATALOG_NAME)
@ScopeDefaultColumn.VirtualColumnDefinition(fieldName = "entityId", columnName = "entity_id", isID = true, type = String.class)
public class K8SServiceInstanceRelation extends K8SMetrics {

    private volatile String entityId;

    private String sourceServiceId;
    @ScopeDefaultColumn.DefinedByField(columnName = "source_service_name", requireDynamicActive = true)
    private String sourceServiceName;
    private String sourceServiceInstanceId;
    @ScopeDefaultColumn.DefinedByField(columnName = "source_service_instance_name", requireDynamicActive = true)
    private String sourceServiceInstanceName;
    private Layer sourceLayer;

    private DetectPoint detectPoint;

    private String destServiceId;
    @ScopeDefaultColumn.DefinedByField(columnName = "dest_service_name", requireDynamicActive = true)
    private String destServiceName;
    private String destServiceInstanceId;
    @ScopeDefaultColumn.DefinedByField(columnName = "dest_service_instance_name", requireDynamicActive = true)
    private String destServiceInstanceName;
    private Layer destLayer;

    @Override
    public int scope() {
        return K8S_SERVICE_INSTANCE_RELATION;
    }

    @Override
    public void prepare() {
        sourceServiceId = IDManager.ServiceID.buildId(sourceServiceName, sourceLayer.isNormal());
        sourceServiceInstanceId = IDManager.ServiceInstanceID.buildId(sourceServiceId, sourceServiceInstanceName);
        destServiceId = IDManager.ServiceID.buildId(destServiceName, destLayer.isNormal());
        destServiceInstanceId = IDManager.ServiceInstanceID.buildId(destServiceId, destServiceInstanceName);

        entityId = IDManager.ServiceInstanceID.buildRelationId(
            new IDManager.ServiceInstanceID.ServiceInstanceRelationDefine(
                sourceServiceInstanceId,
                destServiceInstanceId
            )
        );
    }
}
