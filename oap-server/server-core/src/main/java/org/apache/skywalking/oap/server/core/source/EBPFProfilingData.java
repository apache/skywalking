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
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.EBPF_PROFILING_DATA;

@Data
@ScopeDeclaration(id = EBPF_PROFILING_DATA, name = "EBPFProfilingData")
@ScopeDefaultColumn.VirtualColumnDefinition(fieldName = "entityId", columnName = "entity_id", isID = true, type = String.class)
public class EBPFProfilingData extends Source {
    private volatile String entityId;

    @Override
    public int scope() {
        return EBPF_PROFILING_DATA;
    }

    @Override
    public String getEntityId() {
        if (entityId == null) {
            return scheduleId + Const.ID_CONNECTOR + stackIdList;
        }
        return entityId;
    }

    private String scheduleId;
    private String taskId;
    private long uploadTime;
    private String stackIdList;
    private EBPFProfilingTargetType targetType;
    private byte[] dataBinary;

    @Override
    public String toJson() {
        final JsonObject obj = new JsonObject();
        obj.addProperty("scope", scope());
        obj.addProperty("entityId", getEntityId());
        obj.addProperty("timeBucket", getTimeBucket());
        obj.addProperty("scheduleId", scheduleId);
        obj.addProperty("taskId", taskId);
        obj.addProperty("uploadTime", uploadTime);
        obj.addProperty("stackIdList", stackIdList);
        obj.addProperty("targetType", targetType == null ? null : targetType.name());
        return obj.toString();
    }

}