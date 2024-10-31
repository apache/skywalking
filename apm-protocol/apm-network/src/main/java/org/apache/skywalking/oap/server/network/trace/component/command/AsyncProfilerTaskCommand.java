/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.oap.server.network.trace.component.command;

import lombok.Getter;
import org.apache.skywalking.apm.network.common.v3.Command;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;

import java.util.List;
import java.util.Objects;

@Getter
public class AsyncProfilerTaskCommand extends BaseCommand implements Serializable, Deserializable<AsyncProfilerTaskCommand> {
    public static final Deserializable<AsyncProfilerTaskCommand> DESERIALIZER = new AsyncProfilerTaskCommand("", "", 0, null, "", 0);
    public static final String NAME = "AsyncProfilerTaskQuery";

    /**
     * async-profiler taskId
     */
    private final String taskId;
    /**
     * run profiling for duration (second)
     */
    private final int duration;
    /**
     * async profiler extended parameters. Here is a table of optional parameters.
     *
     * <p>lock[=DURATION]  - profile contended locks overflowing the DURATION ns bucket (default: 10us)</p>
     * <p>alloc[=BYTES]    - profile allocations with BYTES interval</p>
     * <p>interval=N       - sampling interval in ns (default: 10'000'000, i.e. 10 ms)</p>
     * <p>jstackdepth=N    - maximum Java stack depth (default: 2048)</p>
     * <p>chunksize=N      - approximate size of JFR chunk in bytes (default: 100 MB) </p>
     * <p>chunktime=N      - duration of JFR chunk in seconds (default: 1 hour) </p>
     * details @see <a href="https://github.com/async-profiler/async-profiler/blob/master/src/arguments.cpp#L44">async-profiler argument</a>
     */
    private final String execArgs;
    /**
     * task create time
     */
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
            sb.append("event=").append(String.join(comma, events));
        }
        if (execArgs != null && !execArgs.isEmpty()) {
            sb.append(comma).append(execArgs);
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
}