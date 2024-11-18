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

package org.apache.skywalking.oap.server.library.jfr.parser;

import one.jfr.event.ContendedLock;
import one.jfr.event.LiveObject;
import org.apache.skywalking.oap.server.library.jfr.type.Arguments;
import org.apache.skywalking.oap.server.library.jfr.type.CallStack;
import org.apache.skywalking.oap.server.library.jfr.type.Classifier;
import one.jfr.JFRConverter;
import one.jfr.JfrReader;
import one.jfr.StackTrace;
import one.jfr.event.AllocationSample;
import one.jfr.event.Event;
import one.jfr.event.EventAggregator;
import org.apache.skywalking.oap.server.library.jfr.type.FrameTree;
import org.apache.skywalking.oap.server.library.jfr.type.FrameTreeBuilder;
import org.apache.skywalking.oap.server.library.jfr.type.JFREventType;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.apache.skywalking.oap.server.library.jfr.type.Frame.TYPE_INLINED;
import static org.apache.skywalking.oap.server.library.jfr.type.Frame.TYPE_KERNEL;
import static org.apache.skywalking.oap.server.library.jfr.type.Frame.TYPE_NATIVE;

public class JFRToFrameTree extends JFRConverter {

    private final Map<JFREventType, FrameTreeBuilder> event2builderMap = new HashMap<>();

    private final Arguments args;

    public JFRToFrameTree(JfrReader jfr, Arguments arguments) {
        super(jfr);
        this.args = arguments;
    }

    @Override
    protected void convertChunk() throws IOException {
        Map<JFREventType, EventAggregator> event2aggMap = collectMultiEvents();
        for (Map.Entry<JFREventType, EventAggregator> entry : event2aggMap.entrySet()) {
            JFREventType event = entry.getKey();
            EventAggregator agg = entry.getValue();
            FrameTreeBuilder frameTreeBuilder = event2builderMap.computeIfAbsent(event, eventType -> new FrameTreeBuilder());

            agg.forEach(new EventAggregator.Visitor() {
                final CallStack stack = new CallStack();
                final double ticksToNanos = 1e9 / jfr.ticksPerSec;
                final boolean scale = JFREventType.isLockSample(event) && ticksToNanos != 1.0;

                @Override
                public void visit(Event event, long value) {
                    StackTrace stackTrace = jfr.stackTraces.get(event.stackTraceId);
                    if (stackTrace != null) {
                        long[] methods = stackTrace.methods;
                        byte[] types = stackTrace.types;
                        int[] locations = stackTrace.locations;

                        if (args.isThreads()) {
                            stack.push(getThreadName(event.tid), TYPE_NATIVE);
                        }
                        if (args.isClassify()) {
                            Classifier.Category category = getCategory(stackTrace);
                            stack.push(category.getTitle(), category.getType());
                        }
                        for (int i = methods.length; --i >= 0; ) {
                            String methodName = getMethodName(methods[i], types[i]);
                            int location;
                            if ((location = locations[i] >>> 16) != 0) {
                                methodName += ":" + location;
                            }
                            stack.push(methodName, types[i]);
                        }
                        if (event instanceof AllocationSample) {
                            AllocationSample allocationSample = (AllocationSample) event;
                            if (allocationSample.classId != 0) {
                                stack.push(getClassName(allocationSample.classId), ((AllocationSample) event).tlabSize == 0 ? TYPE_KERNEL : TYPE_INLINED);
                            }
                        } else if (event instanceof LiveObject) {
                            LiveObject liveObject = (LiveObject) event;
                            if (liveObject.classId != 0) {
                                stack.push(getClassName(liveObject.classId), TYPE_INLINED);
                            }
                        } else if (event instanceof ContendedLock) {
                            ContendedLock contendedLock = (ContendedLock) event;
                            if (contendedLock.classId != 0) {
                                stack.push(getClassName(contendedLock.classId), TYPE_INLINED);
                            }
                        }

                        frameTreeBuilder.addSample(stack, scale ? (long) (value * ticksToNanos) : value);
                        stack.clear();
                    }
                }
            });
        }
    }

    public Map<JFREventType, FrameTree> getFrameTreeMap() {
        Map<JFREventType, FrameTree> resMap = new HashMap<>();
        for (Map.Entry<JFREventType, FrameTreeBuilder> entry : event2builderMap.entrySet()) {
            JFREventType event = entry.getKey();
            FrameTreeBuilder frameTreeBuilder = entry.getValue();
            resMap.put(event, frameTreeBuilder.build());
        }
        return resMap;
    }

}
