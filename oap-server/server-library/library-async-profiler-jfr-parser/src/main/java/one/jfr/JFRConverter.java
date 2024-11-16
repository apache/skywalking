/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package one.jfr;

import one.jfr.event.AllocationSample;
import one.jfr.event.ContendedLock;
import one.jfr.event.Event;
import one.jfr.event.EventAggregator;
import one.jfr.event.ExecutionSample;
import one.jfr.event.LiveObject;
import org.apache.skywalking.oap.server.library.jfr.type.JFREventType;
import org.apache.skywalking.oap.server.library.jfr.type.Classifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.apache.skywalking.oap.server.library.jfr.type.Frame.TYPE_CPP;
import static org.apache.skywalking.oap.server.library.jfr.type.Frame.TYPE_KERNEL;
import static org.apache.skywalking.oap.server.library.jfr.type.Frame.TYPE_NATIVE;
import static org.apache.skywalking.oap.server.library.jfr.type.JFREventType.EXECUTION_SAMPLE;
import static org.apache.skywalking.oap.server.library.jfr.type.JFREventType.LOCK;
import static org.apache.skywalking.oap.server.library.jfr.type.JFREventType.OBJECT_ALLOCATION_IN_NEW_TLAB;
import static org.apache.skywalking.oap.server.library.jfr.type.JFREventType.OBJECT_ALLOCATION_OUTSIDE_TLAB;
import static org.apache.skywalking.oap.server.library.jfr.type.JFREventType.PROFILER_LIVE_OBJECT;

/**
 * Add collectMultiEvents and getEventAggregator methods to the original code to parse and process multiple events at once
 * <p>
 * This class is placed in the one.jfr package because some classes and fields of the async-profiler-converter package
 * can only be accessed under the same package name, and what we want to do is to expand it, so here we choose to create
 * a class with the same package name for extension.
 */
public abstract class JFRConverter extends Classifier {

    protected final JfrReader jfr;
    protected Dictionary<String> methodNames;

    public JFRConverter(JfrReader jfr) {
        this.jfr = jfr;
    }

    public void convert() throws IOException {
        jfr.stopAtNewChunk = true;
        while (!jfr.eof()) {
            // Reset method dictionary, since new chunk may have different IDs
            methodNames = new Dictionary<>();
            convertChunk();
        }
    }

    protected abstract void convertChunk() throws IOException;

    protected Map<JFREventType, EventAggregator> collectMultiEvents() throws IOException {
        Map<JFREventType, EventAggregator> event2aggMap = new HashMap<>();

        for (Event event; (event = jfr.readEvent()) != null; ) {
            EventAggregator agg = null;
            if (event instanceof ExecutionSample) {
                agg = event2aggMap.computeIfAbsent(EXECUTION_SAMPLE, JFRConverter::getEventAggregator);
            } else if (event instanceof AllocationSample) {
                AllocationSample allocationSample = (AllocationSample) event;
                if (allocationSample.tlabSize != 0) {
                    agg = event2aggMap.computeIfAbsent(OBJECT_ALLOCATION_IN_NEW_TLAB, JFRConverter::getEventAggregator);
                } else {
                    agg = event2aggMap.computeIfAbsent(OBJECT_ALLOCATION_OUTSIDE_TLAB, JFRConverter::getEventAggregator);
                }
            } else if (event instanceof ContendedLock) {
                agg = event2aggMap.computeIfAbsent(LOCK, JFRConverter::getEventAggregator);
            } else if (event instanceof LiveObject) {
                agg = event2aggMap.computeIfAbsent(PROFILER_LIVE_OBJECT, JFRConverter::getEventAggregator);
            }
            if (agg != null) {
                agg.collect(event);
            }
        }

        return event2aggMap;
    }

    private static EventAggregator getEventAggregator(JFREventType jfrEventType) {
        // config aggregator
        switch (jfrEventType) {
            case EXECUTION_SAMPLE:
                return new EventAggregator(true, false);
            case PROFILER_LIVE_OBJECT:
            case OBJECT_ALLOCATION_IN_NEW_TLAB:
            case OBJECT_ALLOCATION_OUTSIDE_TLAB:
            case LOCK:
            case JAVA_MONITOR_ENTER:
            case THREAD_PARK:
                return new EventAggregator(true, true);
            default:
                throw new IllegalStateException("EventAggregator is not support event type : " + jfrEventType);
        }
    }

    @Override
    public String getMethodName(long methodId, byte methodType) {
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
            String classStr = toJavaClassName(className, 0);
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

        String name = toJavaClassName(className, arrayDepth);
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

    protected String toJavaClassName(byte[] symbol, int start) {
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

        // make signature normal
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

        String s = new String(symbol, start, end - start, StandardCharsets.UTF_8);
        return s.replace('/', '.');
    }

    protected boolean isNativeFrame(byte methodType) {
        // In JDK Flight Recorder, TYPE_NATIVE denotes Java native methods,
        // while in async-profiler, TYPE_NATIVE is for C methods
        return methodType == TYPE_NATIVE && jfr.getEnumValue("jdk.types.FrameType", TYPE_KERNEL) != null ||
                methodType == TYPE_CPP ||
                methodType == TYPE_KERNEL;
    }
}
