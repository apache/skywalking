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
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessDetectType;
import org.apache.skywalking.oap.server.core.query.enumeration.ProfilingSupportStatus;

import java.util.List;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.PROCESS;

@ScopeDeclaration(id = PROCESS, name = "Process")
@ScopeDefaultColumn.VirtualColumnDefinition(fieldName = "entityId", columnName = "entity_id", isID = true, type = String.class)
public class Process extends Source {
    private volatile String entityId;

    @Override
    public int scope() {
        return PROCESS;
    }

    @Override
    public String getEntityId() {
        if (entityId == null) {
            entityId = IDManager.ProcessID.buildId(instanceId, name);
        }
        return entityId;
    }

    @Getter
    private String instanceId;
    @Getter
    private String serviceId;
    @Getter
    @Setter
    private String name;
    @Getter
    @Setter
    private String serviceName;
    @Getter
    @Setter
    private String instanceName;
    @Getter
    @Setter
    private boolean isServiceNormal;
    @Getter
    @Setter
    private String agentId;
    @Getter
    @Setter
    private ProcessDetectType detectType;
    @Getter
    @Setter
    private JsonObject properties;
    @Setter
    @Getter
    private List<String> labels;
    @Setter
    @Getter
    private ProfilingSupportStatus profilingSupportStatus;

    @Override
    public void prepare() {
        serviceId = IDManager.ServiceID.buildId(serviceName, isServiceNormal);
        instanceId = IDManager.ServiceInstanceID.buildId(serviceId, instanceName);
    }
}
