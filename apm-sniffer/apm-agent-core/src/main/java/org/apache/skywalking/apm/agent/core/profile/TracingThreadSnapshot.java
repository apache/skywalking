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

package org.apache.skywalking.apm.agent.core.profile;

import org.apache.skywalking.apm.network.language.profile.ProfileTaskSegmentStack;

import java.util.List;

/**
 * @author MrPro
 */
public class TracingThreadSnapshot {

    // thread profiler
    private final ThreadProfiler threadProfiler;

    // dump info
    private final int sequence;
    private final long time;
    private final List<String> stackList;

    public TracingThreadSnapshot(ThreadProfiler threadProfiler, int sequence, long time, List<String> stackList) {
        this.threadProfiler = threadProfiler;
        this.sequence = sequence;
        this.time = time;
        this.stackList = stackList;
    }

    /**
     * transform to gRPC data
     * @return
     */
    public org.apache.skywalking.apm.network.language.profile.ProfileTaskSegmentSnapshot transform() {
        final org.apache.skywalking.apm.network.language.profile.ProfileTaskSegmentSnapshot.Builder builder = org.apache.skywalking.apm.network.language.profile.ProfileTaskSegmentSnapshot.newBuilder();
        // task id
        builder.setTaskId(threadProfiler.getExecutionContext().getTask().getTaskId());
        // dumped segment id
        builder.setTraceSegmentId(threadProfiler.getSegment().getTraceSegmentId().transform());
        // dump time
        builder.setTime(time);
        // snapshot dump sequence
        builder.setSequence(sequence);
        // snapshot stack
        final ProfileTaskSegmentStack.Builder stackBuilder = ProfileTaskSegmentStack.newBuilder();
        for (String codeSign : stackList) {
            stackBuilder.addCodeSignatures(codeSign);
        }
        builder.setStack(stackBuilder);

        return builder.build();
    }


}
