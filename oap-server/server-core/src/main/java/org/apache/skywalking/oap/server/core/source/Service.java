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
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.NodeType;

import java.util.List;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE_CATALOG_NAME;

@ScopeDeclaration(id = SERVICE, name = "Service", catalog = SERVICE_CATALOG_NAME)
@ScopeDefaultColumn.VirtualColumnDefinition(fieldName = "entityId", columnName = "entity_id", isID = true, type = String.class)
public class Service extends Source {
    @Override
    public int scope() {
        return DefaultScopeDefine.SERVICE;
    }

    @Override
    public String getEntityId() {
        return IDManager.ServiceID.buildId(name, nodeType);
    }

    @Getter
    @Setter
    @ScopeDefaultColumn.DefinedByField(columnName = "name", requireDynamicActive = true)
    private String name;
    @Setter
    @Getter
    private NodeType nodeType;
    @Getter
    @Setter
    private String serviceInstanceName;
    @Getter
    @Setter
    private String endpointName;
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
    private List<String> tags;
    @Getter
    @Setter
    private SideCar sideCar = new SideCar();
}
