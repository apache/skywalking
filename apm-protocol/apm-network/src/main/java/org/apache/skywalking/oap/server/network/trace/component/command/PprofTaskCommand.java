package org.apache.skywalking.oap.server.network.trace.component.command;

import org.apache.skywalking.apm.network.common.v3.Command;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import java.util.List;
import lombok.Getter;

@Getter
public class PprofTaskCommand extends BaseCommand implements Serializable, Deserializable<PprofTaskCommand> {
    public static final Deserializable<PprofTaskCommand> DESERIALIZER = new PprofTaskCommand("", "", "", 0, 0, 0);
    public static final String NAME = "PprofTaskQuery";
    /**
     * pprof taskId
     */
    private String taskId;
    // Type of profiling (CPU/Heap/Block/Mutex/Goroutine/Threadcreate/Allocs)
    private String events;
    // unit is minute
    private long duration;
    // Unix timestamp in milliseconds when the task was created
    private long createTime;
    //
    private int dumpPeriod;

    public PprofTaskCommand(String serialNumber, String taskId, String events,
                            long duration, long createTime, int dumpPeriod) {
        super(NAME, serialNumber);
        this.taskId = taskId;
        this.duration = duration;
        this.createTime = createTime;
        this.dumpPeriod = dumpPeriod;
        this.events = events;
    }

    // public PprofTaskCommand(String serialNumber, String taskId,
    //                         long duration, long startTime, long createTime, int dumpPeriod) {
    //     super(NAME, serialNumber);
    //     this.taskId = taskId;
    //     this.duration = duration;
    //     this.startTime = startTime;
    //     this.createTime = createTime;
    //     this.dumpPeriod = dumpPeriod;
    // }

    @Override
    public PprofTaskCommand deserialize(Command command) {
        final List<KeyStringValuePair> argsList = command.getArgsList();
        String taskId = null;
        String events = null;
        long duration = 0;
        long createTime = 0;
        int dumpPeriod = 0;
        String serialNumber = null;
        for (final KeyStringValuePair pair : argsList) {
            if ("SerialNumber".equals(pair.getKey())) {
                serialNumber = pair.getValue();
            } else if ("TaskId".equals(pair.getKey())) {
                taskId = pair.getValue();
            } else if ("Events".equals(pair.getKey())) {
                events = pair.getValue();
            } else if ("Duration".equals(pair.getKey())) {
                duration = Long.parseLong(pair.getValue());
            } else if ("CreateTime".equals(pair.getKey())) {
                createTime = Long.parseLong(pair.getValue());
            } else if ("DumpPeriod".equals(pair.getKey())) {
                dumpPeriod = Integer.parseInt(pair.getValue());
            }
        }
        return new PprofTaskCommand(serialNumber, taskId, events, duration, createTime, dumpPeriod);
    }

    @Override
    public Command.Builder serialize() {
        final Command.Builder builder = commandBuilder();
        builder.addArgs(KeyStringValuePair.newBuilder().setKey("TaskId").setValue(taskId))
                .addArgs(KeyStringValuePair.newBuilder().setKey("Events").setValue(events))
                .addArgs(KeyStringValuePair.newBuilder().setKey("Duration").setValue(String.valueOf(duration)))
                .addArgs(KeyStringValuePair.newBuilder().setKey("CreateTime").setValue(String.valueOf(createTime)))
                .addArgs(KeyStringValuePair.newBuilder().setKey("DumpPeriod").setValue(String.valueOf(dumpPeriod)));
        return builder;
    }
}