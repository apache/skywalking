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

package org.apache.skywalking.apm.network.trace.component.command;

import org.apache.skywalking.apm.network.common.Command;
import org.apache.skywalking.apm.network.common.KeyStringValuePair;

import java.util.List;

/**
 * @author MrPro
 */
public class ProfileTaskCommand extends BaseCommand implements Serializable, Deserializable<ProfileTaskCommand> {
    public static final Deserializable<ProfileTaskCommand> DESERIALIZER = new ProfileTaskCommand("", "", 0, 0, 0, 0, 0, 0);
    public static final String NAME = "ProfileTaskQuery";

    // profile task data
    private String endpointName;
    private int duration;
    private int minDurationThreshold;
    private int dumpPeriod;
    private int maxSamplingCount;
    private long startTime;
    private long createTime;

    public ProfileTaskCommand(String serialNumber, String endpointName, int duration, int minDurationThreshold, int dumpPeriod, int maxSamplingCount, long startTime, long createTime) {
        super(NAME, serialNumber);
        this.endpointName = endpointName;
        this.duration = duration;
        this.minDurationThreshold = minDurationThreshold;
        this.dumpPeriod = dumpPeriod;
        this.maxSamplingCount = maxSamplingCount;
        this.startTime = startTime;
        this.createTime = createTime;
    }

    @Override
    public ProfileTaskCommand deserialize(Command command) {
        final List<KeyStringValuePair> argsList = command.getArgsList();
        String serialNumber = null;
        String endpointName = null;
        int duration = 0;
        int minDurationThreshold = 0;
        int dumpPeriod = 0;
        int maxSamplingCount = 0;
        long startTime = 0;
        long createTime = 0;

        for (final KeyStringValuePair pair : argsList) {
            if ("SerialNumber".equals(pair.getKey())) {
                serialNumber = pair.getValue();
            } else if ("EndpointName".equals(pair.getKey())) {
                endpointName = pair.getValue();
            } else if ("Duration".equals(pair.getKey())) {
                duration = Integer.parseInt(pair.getValue());
            } else if ("MinDurationThreshold".equals(pair.getKey())) {
                minDurationThreshold = Integer.parseInt(pair.getValue());
            } else if ("DumpPeriod".equals(pair.getKey())) {
                dumpPeriod = Integer.parseInt(pair.getValue());
            } else if ("MaxSamplingCount".equals(pair.getKey())) {
                maxSamplingCount = Integer.parseInt(pair.getValue());
            } else if ("StartTime".equals(pair.getKey())) {
                startTime = Long.parseLong(pair.getValue());
            } else if ("CreateTime".equals(pair.getKey())) {
                createTime = Long.parseLong(pair.getValue());
            }
        }

        return new ProfileTaskCommand(serialNumber, endpointName, duration, minDurationThreshold, dumpPeriod, maxSamplingCount, startTime, createTime);
    }

    @Override
    public Command.Builder serialize() {
        final Command.Builder builder = commandBuilder();
        builder.addArgs(KeyStringValuePair.newBuilder().setKey("EndpointName").setValue(endpointName))
                .addArgs(KeyStringValuePair.newBuilder().setKey("Duration").setValue(String.valueOf(duration)))
                .addArgs(KeyStringValuePair.newBuilder().setKey("MinDurationThreshold").setValue(String.valueOf(minDurationThreshold)))
                .addArgs(KeyStringValuePair.newBuilder().setKey("DumpPeriod").setValue(String.valueOf(dumpPeriod)))
                .addArgs(KeyStringValuePair.newBuilder().setKey("MaxSamplingCount").setValue(String.valueOf(maxSamplingCount)))
                .addArgs(KeyStringValuePair.newBuilder().setKey("StartTime").setValue(String.valueOf(startTime)))
                .addArgs(KeyStringValuePair.newBuilder().setKey("CreateTime").setValue(String.valueOf(createTime)));
        return builder;
    }

    public String getEndpointName() {
        return endpointName;
    }

    public int getDuration() {
        return duration;
    }

    public int getMinDurationThreshold() {
        return minDurationThreshold;
    }

    public int getDumpPeriod() {
        return dumpPeriod;
    }

    public int getMaxSamplingCount() {
        return maxSamplingCount;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getCreateTime() {
        return createTime;
    }
}
