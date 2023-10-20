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

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import lombok.Getter;
import lombok.Setter;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.EBPF_PROFILING_SCHEDULE;

@Setter
@Getter
@ScopeDeclaration(id = EBPF_PROFILING_SCHEDULE, name = "EBPFProcessProfilingSchedule")
@ScopeDefaultColumn.VirtualColumnDefinition(fieldName = "entityId", columnName = "entity_id", isID = true, type = String.class)
public class EBPFProcessProfilingSchedule extends Source {
    private volatile String entityId;

    @Override
    public int scope() {
        return EBPF_PROFILING_SCHEDULE;
    }

    @Override
    public String getEntityId() {
        if (entityId == null) {
            entityId = Hashing.sha256().newHasher()
                              .putString(String.format("%s_%s_%d", taskId, processId, startTime), Charsets.UTF_8)
                              .hash().toString();
        }
        return entityId;
    }

    private String processId;
    private String taskId;
    private long startTime;
    private long currentTime;
}
