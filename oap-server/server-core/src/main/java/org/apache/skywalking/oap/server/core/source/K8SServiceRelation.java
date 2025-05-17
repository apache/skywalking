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
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.AccessLogConnectionTLSMode;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;

import java.util.HashSet;
import java.util.Set;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.K8S_SERVICE_RELATION;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE_RELATION_CATALOG_NAME;

@Data
@ScopeDeclaration(id = K8S_SERVICE_RELATION, name = "K8SServiceRelation", catalog = SERVICE_RELATION_CATALOG_NAME)
@ScopeDefaultColumn.VirtualColumnDefinition(fieldName = "entityId", columnName = "entity_id", isID = true, type = String.class)
public class K8SServiceRelation extends K8SMetrics {

    private volatile String entityId;

    private String sourceServiceId;
    @ScopeDefaultColumn.DefinedByField(columnName = "source_name", requireDynamicActive = true)
    private String sourceServiceName;
    private Layer sourceLayer;

    private DetectPoint detectPoint;
    private final Set<Integer> componentId = new HashSet<>();
    private AccessLogConnectionTLSMode tlsMode;

    private String destServiceId;
    @ScopeDefaultColumn.DefinedByField(columnName = "dest_name", requireDynamicActive = true)
    private String destServiceName;
    private Layer destLayer;

    @Override
    public int scope() {
        return K8S_SERVICE_RELATION;
    }

    @Override
    public void prepare() {
        sourceServiceId = IDManager.ServiceID.buildId(sourceServiceName, sourceLayer.isNormal());
        destServiceId = IDManager.ServiceID.buildId(destServiceName, destLayer.isNormal());

        entityId = IDManager.ServiceID.buildRelationId(
            new IDManager.ServiceID.ServiceRelationDefine(
                sourceServiceId,
                destServiceId
            )
        );
    }
}
