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
    /**
     * event type of profiling (CPU/Heap/Block/Mutex/Goroutine/Threadcreate/Allocs)
     */
    private String events;
    /**
     * run profiling for duration (minute)
     */
    private long duration;
    /**
     * task create time
     */
    private long createTime;
    /**
     * pprof dump period parameters. There are different dumpperiod configurations for different events. 
     * Here is a table of parameters.
     *
     * <p>for Block - sample an average of one blocking event per rate nanoseconds spent blocked. (default: 0)</p>
     * <p>for Mutex - sample an average of 1/rate events are reported. (default: 0)</p>
     * details @see <a href="https://pkg.go.dev/runtime/pprof">pprof argument</a>
     */
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