package org.apache.skywalking.oap.server.network.trace.component.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.skywalking.apm.network.common.v3.Command;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;

public class EBPFProcessProfilingTaskCommand extends BaseCommand implements Serializable {
    public static final String NAME = "EBPFProcessProfilingTaskQuery";

    private String taskId;
    private String processId;
    private long taskStartTime;
    private long taskUpdateTime;
    private String triggerType;
    private FixedTrigger fixedTrigger;
    private String targetType;

    public EBPFProcessProfilingTaskCommand(String serialNumber, String taskId, String processId, long taskStartTime,
                                           long taskUpdateTime, String triggerType, FixedTrigger fixedTrigger,
                                           String targetType) {
        super(NAME, serialNumber);
        this.taskId = taskId;
        this.processId = processId;
        this.taskStartTime = taskStartTime;
        this.taskUpdateTime = taskUpdateTime;
        this.triggerType = triggerType;
        this.fixedTrigger = fixedTrigger;
        this.targetType = targetType;
    }

    @Override
    public Command.Builder serialize() {
        final Command.Builder builder = commandBuilder();
        builder.addArgs(KeyStringValuePair.newBuilder().setKey("TaskId").setValue(taskId))
                .addArgs(KeyStringValuePair.newBuilder().setKey("ProcessId").setValue(processId))
                .addArgs(KeyStringValuePair.newBuilder().setKey("TaskUpdateTime").setValue(String.valueOf(taskUpdateTime)))
                .addArgs(KeyStringValuePair.newBuilder().setKey("TriggerType").setValue(triggerType))
                .addArgs(KeyStringValuePair.newBuilder().setKey("TargetType").setValue(targetType))
                .addArgs(KeyStringValuePair.newBuilder().setKey("TaskStartTime").setValue(String.valueOf(taskStartTime)));

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