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
import org.apache.skywalking.oap.server.core.query.type.PprofEventType;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.PPROF_PROFILING_DATA;

@Data
@ScopeDeclaration(id = PPROF_PROFILING_DATA, name = "PprofProfilingData")
@ScopeDefaultColumn.VirtualColumnDefinition(fieldName = "entityId", columnName = "entity_id", isID = true, type = String.class)
public class PprofProfilingData extends Source {
    private volatile String entityId;

    @Override
    public int scope() {
        return PPROF_PROFILING_DATA;
    }

    @Override
    public String getEntityId() {
        if (entityId == null) {
            return taskId + instanceId + eventType.name() + uploadTime;
        }
        return entityId;
    }

    private String taskId;
    private String instanceId;
    private long uploadTime;
    private PprofEventType eventType;
    private Object frameTree;
} 