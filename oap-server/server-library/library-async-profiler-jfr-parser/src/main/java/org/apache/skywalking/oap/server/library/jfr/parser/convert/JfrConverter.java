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

/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.skywalking.oap.server.library.jfr.parser.convert;

import org.apache.skywalking.oap.server.library.jfr.parser.type.ClassRef;
import org.apache.skywalking.oap.server.library.jfr.parser.type.Dictionary;
import org.apache.skywalking.oap.server.library.jfr.parser.type.JfrReader;
import org.apache.skywalking.oap.server.library.jfr.parser.type.MethodRef;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.AllocationSample;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.ContendedLock;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.Event;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.EventAggregator;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.EventPair;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.ExecutionSample;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.JFREventType;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.LiveObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.apache.skywalking.oap.server.library.jfr.parser.convert.Frame.Entry;
import static org.apache.skywalking.oap.server.library.jfr.parser.convert.Frame.TYPE_CPP;
import static org.apache.skywalking.oap.server.library.jfr.parser.convert.Frame.TYPE_KERNEL;
import static org.apache.skywalking.oap.server.library.jfr.parser.convert.Frame.TYPE_NATIVE;
import static org.apache.skywalking.oap.server.library.jfr.parser.type.event.JFREventType.EXECUTION_SAMPLE;
import static org.apache.skywalking.oap.server.library.jfr.parser.type.event.JFREventType.JAVA_MONITOR_ENTER;
import static org.apache.skywalking.oap.server.library.jfr.parser.type.event.JFREventType.OBJECT_ALLOCATION_IN_NEW_TLAB;
import static org.apache.skywalking.oap.server.library.jfr.parser.type.event.JFREventType.OBJECT_ALLOCATION_OUTSIDE_TLAB;
import static org.apache.skywalking.oap.server.library.jfr.parser.type.event.JFREventType.PROFILER_LIVE_OBJECT;
import static org.apache.skywalking.oap.server.library.jfr.parser.type.event.JFREventType.THREAD_PARK;

public abstract class JfrConverter extends Classifier {
    protected final JfrReader jfr;
    protected final Arguments args;
    protected Dictionary<String> methodNames;

    public JfrConverter(JfrReader jfr, Arguments args) {
        this.jfr = jfr;
        this.args = args;
    }

    public void convert() throws IOException {
        jfr.stopAtNewChunk = true;
        while (jfr.hasMoreChunks()) {
            // Reset method dictionary, since new chunk may have different IDs
            methodNames = new Dictionary<>();
            convertChunk();
        }
    }

    protected abstract void convertChunk() throws IOException;

    protected EventAggregator collectEvents() throws IOException {
        EventAggregator agg = new EventAggregator(args.threads, args.total);

        Class<? extends Event> eventClass =
                args.live ? LiveObject.class :
                        args.alloc ? AllocationSample.class :
                                args.lock ? ContendedLock.class : ExecutionSample.class;

        long threadStates = 0;
        if (args.state != null) {
            for (String state : args.state.toUpperCase().split(",")) {
                threadStates |= 1L << toThreadState(state);
            }
        } else if (args.cpu) {
            threadStates = 1L << toThreadState("DEFAULT");
        } else if (args.wall) {
            threadStates = ~(1L << toThreadState("DEFAULT"));
        }

        long startTicks = args.from != 0 ? toTicks(args.from) : Long.MIN_VALUE;
        long endTicks = args.to != 0 ? toTicks(args.to) : Long.MAX_VALUE;

        for (Event event; (event = jfr.readEvent(eventClass)) != null; ) {
            if (event.time >= startTicks && event.time <= endTicks) {
                if (threadStates == 0 || (threadStates & (1L << ((ExecutionSample) event).threadState)) != 0) {
                    agg.collect(event);
                }
            }
        }

        return agg;
    }

    protected Map<JFREventType, EventAggregator> collectMultiEvents() throws IOException {
        Map<JFREventType, EventAggregator> event2aggMap = new HashMap<>();

        long threadStates = 0;
        if (args.state != null) {
            for (String state : args.state.toUpperCase().split(",")) {
                threadStates |= 1L << toThreadState(state);
            }
        }

        long startTicks = args.from != 0 ? toTicks(args.from) : Long.MIN_VALUE;
        long endTicks = args.to != 0 ? toTicks(args.to) : Long.MAX_VALUE;
        for (EventPair eventPair; (eventPair = jfr.readEventWithType()) != null; ) {
            JFREventType type = eventPair.getType();
            Event event = eventPair.getEvent();
            if (event.time >= startTicks && event.time <= endTicks) {
                EventAggregator agg;
                switch (type) {
                    case EXECUTION_SAMPLE:
                        agg = event2aggMap.computeIfAbsent(EXECUTION_SAMPLE, JfrConverter::getExecutionSampleAggregator);
                        break;
                    case OBJECT_ALLOCATION_IN_NEW_TLAB:
                        agg = event2aggMap.computeIfAbsent(OBJECT_ALLOCATION_IN_NEW_TLAB, JfrConverter::getExecutionSampleAggregator);
                        break;
                    case OBJECT_ALLOCATION_OUTSIDE_TLAB:
                        agg = event2aggMap.computeIfAbsent(OBJECT_ALLOCATION_OUTSIDE_TLAB, JfrConverter::getExecutionSampleAggregator);
                        break;
                    case THREAD_PARK:
                        agg = event2aggMap.computeIfAbsent(THREAD_PARK, JfrConverter::getExecutionSampleAggregator);
                        break;
                    case JAVA_MONITOR_ENTER:
                        agg = event2aggMap.computeIfAbsent(JAVA_MONITOR_ENTER, JfrConverter::getExecutionSampleAggregator);
                        break;
                    case PROFILER_LIVE_OBJECT:
                        agg = event2aggMap.computeIfAbsent(PROFILER_LIVE_OBJECT, JfrConverter::getExecutionSampleAggregator);
                        break;
                    default:
                        throw new RuntimeException("Unknown event type: " + type);
                }
                if (!(event instanceof ExecutionSample) || (threadStates & (1L << ((ExecutionSample) event).threadState)) != 0) {
                    agg.collect(event);
                }
            }
        }

        return event2aggMap;
    }

    private static EventAggregator getExecutionSampleAggregator(JFREventType jfrEventType) {
        // TODO aggregator default configure
        switch (jfrEventType) {
            case EXECUTION_SAMPLE:
                return new EventAggregator(false, false);
            case OBJECT_ALLOCATION_IN_NEW_TLAB:
            case OBJECT_ALLOCATION_OUTSIDE_TLAB:
                return new EventAggregator(false, true);
            case THREAD_PARK:
                return new EventAggregator(true, true);
            case JAVA_MONITOR_ENTER:
                return new EventAggregator(true, false);
            case PROFILER_LIVE_OBJECT:
                return new EventAggregator(true, false);
            default:
                return new EventAggregator(false, false);
        }
    }

    protected int toThreadState(String name) {
        Map<Integer, String> threadStates = jfr.enums.get("jdk.types.ThreadState");
        if (threadStates != null) {
            for (Entry<Integer, String> entry : threadStates.entrySet()) {
                if (entry.getValue().startsWith(name, 6)) {
                    return entry.getKey();
                }
            }
        }
        throw new IllegalArgumentException("Unknown thread state: " + name);
    }

    // millis can be an absolute timestamp or an offset from the beginning/end of the recording
    protected long toTicks(long millis) {
        long nanos = millis * 1_000_000;
        if (millis < 0) {
            nanos += jfr.endNanos;
        } else if (millis < 1500000000000L) {
            nanos += jfr.startNanos;
        }
        return (long) ((nanos - jfr.chunkStartNanos) * (jfr.ticksPerSec / 1e9)) + jfr.chunkStartTicks;
    }

    @Override
    protected String getMethodName(long methodId, byte methodType) {
        String result = methodNames.get(methodId);
        if (result == null) {
            methodNames.put(methodId, result = resolveMethodName(methodId, methodType));
        }
        return result;
    }

    private String resolveMethodName(long methodId, byte methodType) {
        MethodRef method = jfr.methods.get(methodId);
        if (method == null) {
            return "unknown";
        }

        ClassRef cls = jfr.classes.get(method.cls);
        byte[] className = jfr.symbols.get(cls.name);
        byte[] methodName = jfr.symbols.get(method.name);

        if (className == null || className.length == 0 || isNativeFrame(methodType)) {
            return new String(methodName, StandardCharsets.UTF_8);
        } else {
            String classStr = toJavaClassName(className, 0, args.dot);
            if (methodName == null || methodName.length == 0) {
                return classStr;
            }
            String methodStr = new String(methodName, StandardCharsets.UTF_8);
            return classStr + '.' + methodStr;
        }
    }

    protected String getClassName(long classId) {
        ClassRef cls = jfr.classes.get(classId);
        if (cls == null) {
            return "null";
        }
        byte[] className = jfr.symbols.get(cls.name);

        int arrayDepth = 0;
        while (className[arrayDepth] == '[') {
            arrayDepth++;
        }

        String name = toJavaClassName(className, arrayDepth, true);
        while (arrayDepth-- > 0) {
            name = name.concat("[]");
        }
        return name;
    }

    protected String getThreadName(int tid) {
        String threadName = jfr.threads.get(tid);
        return threadName == null ? "[tid=" + tid + ']' :
                threadName.startsWith("[tid=") ? threadName : '[' + threadName + " tid=" + tid + ']';
    }

    protected String toJavaClassName(byte[] symbol, int start, boolean dotted) {
        int end = symbol.length;
        if (start > 0) {
            switch (symbol[start]) {
                case 'B':
                    return "byte";
                case 'C':
                    return "char";
                case 'S':
                    return "short";
                case 'I':
                    return "int";
                case 'J':
                    return "long";
                case 'Z':
                    return "boolean";
                case 'F':
                    return "float";
                case 'D':
                    return "double";
                case 'L':
                    start++;
                    end--;
            }
        }

        if (args.norm) {
            for (int i = end - 2; i > start; i--) {
                if (symbol[i] == '/' || symbol[i] == '.') {
                    if (symbol[i + 1] >= '0' && symbol[i + 1] <= '9') {
                        end = i;
                        if (i > start + 19 && symbol[i - 19] == '+' && symbol[i - 18] == '0') {
                            // Original JFR transforms lambda names to something like
                            // pkg.ClassName$$Lambda+0x00007f8177090218/543846639
                            end = i - 19;
                        }
                    }
                    break;
                }
            }
        }

        if (args.simple) {
            for (int i = end - 2; i >= start; i--) {
                if (symbol[i] == '/' && (symbol[i + 1] < '0' || symbol[i + 1] > '9')) {
                    start = i + 1;
                    break;
                }
            }
        }

        String s = new String(symbol, start, end - start, StandardCharsets.UTF_8);
        return dotted ? s.replace('/', '.') : s;
    }

    protected boolean isNativeFrame(byte methodType) {
        // In JDK Flight Recorder, TYPE_NATIVE denotes Java native methods,
        // while in async-profiler, TYPE_NATIVE is for C methods
        return methodType == TYPE_NATIVE && jfr.getEnumValue("jdk.types.FrameType", TYPE_KERNEL) != null ||
                methodType == TYPE_CPP ||
                methodType == TYPE_KERNEL;
    }
}
