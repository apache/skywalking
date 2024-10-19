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

package org.apache.skywalking.oap.server.library.jfr.parser.convert;

import org.apache.skywalking.oap.server.library.jfr.parser.type.JfrReader;
import org.apache.skywalking.oap.server.library.jfr.parser.type.StackTrace;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.AllocationSample;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.Event;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.EventAggregator;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.JFREventType;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.apache.skywalking.oap.server.library.jfr.parser.convert.Frame.TYPE_INLINED;
import static org.apache.skywalking.oap.server.library.jfr.parser.convert.Frame.TYPE_KERNEL;
import static org.apache.skywalking.oap.server.library.jfr.parser.convert.Frame.TYPE_NATIVE;

public class JfrToFrameTree extends JfrConverter {

    private final Map<JFREventType, FrameTreeBuilder> event2builderMap = new HashMap<>();

    public JfrToFrameTree(JfrReader jfr, Arguments args) {
        super(jfr, args);
    }

    @Override
    protected void convertChunk() throws IOException {
        Map<JFREventType, EventAggregator> event2aggMap = collectMultiEvents();
        for (Map.Entry<JFREventType, EventAggregator> entry : event2aggMap.entrySet()) {
            JFREventType event = entry.getKey();
            EventAggregator agg = entry.getValue();
            FrameTreeBuilder frameTreeBuilder = event2builderMap.computeIfAbsent(event, eventType -> new FrameTreeBuilder(args));

            agg.forEach(new EventAggregator.Visitor() {
                final CallStack stack = new CallStack();
                final double ticksToNanos = 1e9 / jfr.ticksPerSec;
                final boolean scale = args.total && args.lock && ticksToNanos != 1.0;

                @Override
                public void visit(Event event, long value) {
                    StackTrace stackTrace = jfr.stackTraces.get(event.stackTraceId);
                    if (stackTrace != null) {
                        Arguments args = JfrToFrameTree.this.args;
                        long[] methods = stackTrace.methods;
                        byte[] types = stackTrace.types;
                        int[] locations = stackTrace.locations;

                        if (args.threads) {
                            stack.push(getThreadName(event.tid), TYPE_NATIVE);
                        }
                        if (args.classify) {
                            Category category = getCategory(stackTrace);
                            stack.push(category.title, category.type);
                        }
                        for (int i = methods.length; --i >= 0; ) {
                            String methodName = getMethodName(methods[i], types[i]);
                            int location;
                            if (args.lines && (location = locations[i] >>> 16) != 0) {
                                methodName += ":" + location;
                            } else if (args.bci && (location = locations[i] & 0xffff) != 0) {
                                methodName += "@" + location;
                            }
                            stack.push(methodName, types[i]);
                        }
                        long classId = event.classId();
                        if (classId != 0) {
                            stack.push(getClassName(classId), (event instanceof AllocationSample)
                                    && ((AllocationSample) event).tlabSize == 0 ? TYPE_KERNEL : TYPE_INLINED);
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
