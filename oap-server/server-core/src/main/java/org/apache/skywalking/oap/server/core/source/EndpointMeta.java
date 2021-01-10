/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.core.source;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.NodeType;

@ScopeDeclaration(id = DefaultScopeDefine.ENDPOINT_META, name = "EndpointMeta")
@ScopeDefaultColumn.VirtualColumnDefinition(fieldName = "entityId", columnName = "entity_id", isID = true, type = String.class)
public class EndpointMeta extends Source {

    @Getter
    private String serviceId;
    @Getter
    @Setter
    private String serviceName;
    @Getter
    @Setter
    private NodeType serviceNodeType;
    @Getter
    @Setter
    private String endpoint;

    @Override
    public int scope() {
        return DefaultScopeDefine.ENDPOINT_META;
    }

    @Override
    public String getEntityId() {
        return IDManager.EndpointID.buildId(serviceId, endpoint);
    }

    @Override
    public void prepare() {
        this.serviceId = IDManager.ServiceID.buildId(serviceName, serviceNodeType);
    }
}
