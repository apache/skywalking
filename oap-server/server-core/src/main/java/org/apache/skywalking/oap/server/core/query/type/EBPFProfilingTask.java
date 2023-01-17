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

package org.apache.skywalking.oap.server.core.query.type;

import lombok.Data;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;

import java.util.List;

@Data
public class EBPFProfilingTask {

    private String taskId;
    private String serviceId;
    private String serviceName;
    private String serviceInstanceId;
    private String serviceInstanceName;
    private List<String> processLabels;
    private long taskStartTime;
    private EBPFProfilingTriggerType triggerType;
    private long fixedTriggerDuration;
    private EBPFProfilingTargetType targetType;
    private long createTime;
    private long lastUpdateTime;
    private EBPFProfilingTaskExtension extensionConfig;

    /**
     * combine the same task
     * @param task have same {@link #taskId}
     */
    public EBPFProfilingTask combine(EBPFProfilingTask task) {
        if (task.getFixedTriggerDuration() > this.getFixedTriggerDuration()) {
            this.setFixedTriggerDuration(task.getFixedTriggerDuration());
        }
        if (task.getLastUpdateTime() > this.getLastUpdateTime()) {
            this.setLastUpdateTime(task.getLastUpdateTime());
        }
        return this;
    }
}