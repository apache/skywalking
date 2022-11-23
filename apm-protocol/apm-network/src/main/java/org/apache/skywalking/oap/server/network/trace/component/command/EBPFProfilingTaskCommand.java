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

package org.apache.skywalking.oap.server.network.trace.component.command;

import com.google.common.base.Joiner;
import com.google.gson.Gson;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.skywalking.apm.network.common.v3.Command;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;

import java.util.List;

/**
 * eBPF profiling task command, OAP uses this to send a task to the ebpf agent side
 */
public class EBPFProfilingTaskCommand extends BaseCommand implements Serializable {
    public static final String NAME = "EBPFProfilingTaskQuery";
    private static final Gson GSON = new Gson();

    private String taskId;
    private List<String> processIdList;
    private long taskStartTime;
    private long taskUpdateTime;
    private String triggerType;
    private FixedTrigger fixedTrigger;
    private String targetType;
    private EBPFProfilingTaskExtensionConfig extensionConfig;

    public EBPFProfilingTaskCommand(String serialNumber, String taskId, List<String> processIdList, long taskStartTime,
                                    long taskUpdateTime, String triggerType, FixedTrigger fixedTrigger,
                                    String targetType, EBPFProfilingTaskExtensionConfig extensionConfig) {
        super(NAME, serialNumber);
        this.taskId = taskId;
        this.processIdList = processIdList;
        this.taskStartTime = taskStartTime;
        this.taskUpdateTime = taskUpdateTime;
        this.triggerType = triggerType;
        this.fixedTrigger = fixedTrigger;
        this.targetType = targetType;
        this.extensionConfig = extensionConfig;
    }

    @Override
    public Command.Builder serialize() {
        final Command.Builder builder = commandBuilder();
        builder.addArgs(KeyStringValuePair.newBuilder().setKey("TaskId").setValue(taskId))
                .addArgs(KeyStringValuePair.newBuilder().setKey("ProcessId").setValue(Joiner.on(",").join(processIdList)))
                .addArgs(KeyStringValuePair.newBuilder().setKey("TaskUpdateTime").setValue(String.valueOf(taskUpdateTime)))
                .addArgs(KeyStringValuePair.newBuilder().setKey("TriggerType").setValue(triggerType))
                .addArgs(KeyStringValuePair.newBuilder().setKey("TargetType").setValue(targetType))
                .addArgs(KeyStringValuePair.newBuilder().setKey("TaskStartTime").setValue(String.valueOf(taskStartTime)))
                .addArgs(KeyStringValuePair.newBuilder().setKey("ExtensionConfigJSON").setValue(GSON.toJson(extensionConfig)));

        if (fixedTrigger != null) {
            builder.addArgs(KeyStringValuePair.newBuilder().setKey("FixedTriggerDuration").setValue(String.valueOf(fixedTrigger.duration)));
        }
        return builder;
    }

    @Data
    @AllArgsConstructor
    public static class FixedTrigger {
        private long duration;
    }
}