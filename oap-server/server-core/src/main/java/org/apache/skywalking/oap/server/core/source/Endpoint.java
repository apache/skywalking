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
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.ENDPOINT;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.ENDPOINT_CATALOG_NAME;

@ScopeDeclaration(id = ENDPOINT, name = "Endpoint", catalog = ENDPOINT_CATALOG_NAME)
@ScopeDefaultColumn.VirtualColumnDefinition(fieldName = "entityId", columnName = "entity_id", isID = true, type = String.class)
public class Endpoint extends Source {
    @Override
    public int scope() {
        return DefaultScopeDefine.ENDPOINT;
    }

    /**
     * @since 7.1.0 SkyWalking doesn't do endpoint register. Use name directly.
     */
    @Override
    public String getEntityId() {
        return EndpointTraffic.buildId(serviceId, name, DetectPoint.SERVER);
    }

    @Getter
    @ScopeDefaultColumn.DefinedByField(columnName = "name", requireDynamicActive = true)
    private String name;

    public void setName(final String name) {
        this.name = CoreModule.formatEndpointName(name);
    }

    @Getter
    @Setter
    @ScopeDefaultColumn.DefinedByField(columnName = "service_id")
    private int serviceId;
    @Getter
    @Setter
    @ScopeDefaultColumn.DefinedByField(columnName = "service_name", requireDynamicActive = true)
    private String serviceName;
    @Getter
    @Setter
    @ScopeDefaultColumn.DefinedByField(columnName = "service_instance_id")
    private int serviceInstanceId;
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
    private int responseCode;
    @Getter
    @Setter
    private RequestType type;
}
