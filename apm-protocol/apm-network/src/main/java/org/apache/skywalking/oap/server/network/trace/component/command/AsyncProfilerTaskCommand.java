package org.apache.skywalking.oap.server.network.trace.component.command;

import org.apache.skywalking.apm.network.common.v3.Command;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;

import java.util.List;
import java.util.Objects;

public class AsyncProfilerTaskCommand extends BaseCommand implements Serializable, Deserializable<AsyncProfilerTaskCommand> {
    public static final Deserializable<AsyncProfilerTaskCommand> DESERIALIZER = new AsyncProfilerTaskCommand("", "", 0, null, "", 0);
    public static final String NAME = "AsyncProfileTaskQuery";

    private final String taskId;
    private final int duration;
    private final String execArgs;
    private final long createTime;

    public AsyncProfilerTaskCommand(String serialNumber, String taskId, int duration,
                                    List<String> events, String execArgs, long createTime) {
        super(NAME, serialNumber);
        this.taskId = taskId;
        this.duration = duration;
        this.createTime = createTime;
        String comma = ",";
        StringBuilder sb = new StringBuilder();
        if (Objects.nonNull(events) && !events.isEmpty()) {
            sb.append("event=")
                    .append(String.join(comma, events))
                    .append(comma);
        }
        if (execArgs != null && !execArgs.isEmpty()) {
            sb.append(execArgs);
        }
        this.execArgs = sb.toString();
    }

    public AsyncProfilerTaskCommand(String serialNumber, String taskId, int duration,
                                    String execArgs, long createTime) {
        super(NAME, serialNumber);
        this.taskId = taskId;
        this.duration = duration;
        this.execArgs = execArgs;
        this.createTime = createTime;
    }

    @Override
    public AsyncProfilerTaskCommand deserialize(Command command) {
        final List<KeyStringValuePair> argsList = command.getArgsList();
        String taskId = null;
        int duration = 0;
        String execArgs = null;
        long createTime = 0;
        String serialNumber = null;
        for (final KeyStringValuePair pair : argsList) {
            if ("SerialNumber".equals(pair.getKey())) {
                serialNumber = pair.getValue();
            } else if ("TaskId".equals(pair.getKey())) {
                taskId = pair.getValue();
            } else if ("Duration".equals(pair.getKey())) {
                duration = Integer.parseInt(pair.getValue());
            } else if ("ExecArgs".equals(pair.getKey())) {
                execArgs = pair.getValue();
            } else if ("CreateTime".equals(pair.getKey())) {
                createTime = Long.parseLong(pair.getValue());
            }
        }
        return new AsyncProfilerTaskCommand(serialNumber, taskId, duration, execArgs, createTime);
    }

    @Override
    public Command.Builder serialize() {
        final Command.Builder builder = commandBuilder();
        builder.addArgs(KeyStringValuePair.newBuilder().setKey("TaskId").setValue(taskId))
                .addArgs(KeyStringValuePair.newBuilder().setKey("Duration").setValue(String.valueOf(duration)))
                .addArgs(KeyStringValuePair.newBuilder().setKey("ExecArgs").setValue(execArgs))
                .addArgs(KeyStringValuePair.newBuilder().setKey("CreateTime").setValue(String.valueOf(createTime)));
        return builder;
    }

    public String getTaskId() {
        return taskId;
    }

    public int getDuration() {
        return duration;
    }

    public String getExecArgs() {
        return execArgs;
    }

    public long getCreateTime() {
        return createTime;
    }
}