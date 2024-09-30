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

package org.apache.skywalking.oap.server.library.jfr.parser.type;

import org.apache.skywalking.oap.server.library.jfr.parser.type.event.AllocationSample;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.CPULoad;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.ContendedLock;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.Event;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.EventPair;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.ExecutionSample;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.GCHeapSummary;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.JfrEventType;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.LiveObject;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.ObjectCount;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses JFR output produced by async-profiler.
 */
public class JfrReader implements Closeable {
    private static final int BUFFER_SIZE = 2 * 1024 * 1024;
    private static final int CHUNK_HEADER_SIZE = 68;
    private static final int CHUNK_SIGNATURE = 0x464c5200;

    private static final byte STATE_NEW_CHUNK = 0;
    private static final byte STATE_READING = 1;
    private static final byte STATE_EOF = 2;
    private static final byte STATE_INCOMPLETE = 3;

    private final FileChannel ch;
    private ByteBuffer buf;
    private final long fileSize;
    private long filePosition;
    private byte state;

    public long startNanos = Long.MAX_VALUE;
    public long endNanos = Long.MIN_VALUE;
    public long startTicks = Long.MAX_VALUE;
    public long chunkStartNanos;
    public long chunkEndNanos;
    public long chunkStartTicks;
    public long ticksPerSec;
    public boolean stopAtNewChunk;

    public final Dictionary<JfrClass> types = new Dictionary<>();
    public final Map<String, JfrClass> typesByName = new HashMap<>();
    public final Dictionary<String> threads = new Dictionary<>();
    public final Dictionary<ClassRef> classes = new Dictionary<>();
    public final Dictionary<String> strings = new Dictionary<>();
    public final Dictionary<byte[]> symbols = new Dictionary<>();
    public final Dictionary<MethodRef> methods = new Dictionary<>();
    public final Dictionary<StackTrace> stackTraces = new Dictionary<>();
    public final Map<String, String> settings = new HashMap<>();
    public final Map<String, Map<Integer, String>> enums = new HashMap<>();

    private final Dictionary<Constructor<? extends Event>> customEvents = new Dictionary<>();

    private int executionSample;
    private int nativeMethodSample;
    private int allocationInNewTLAB;
    private int allocationOutsideTLAB;
    private int allocationSample;
    private int liveObject;
    private int monitorEnter;
    private int threadPark;
    private int activeSetting;

    public JfrReader(String fileName) throws IOException {
        this.ch = FileChannel.open(Paths.get(fileName), StandardOpenOption.READ);
        this.buf = ByteBuffer.allocateDirect(BUFFER_SIZE);
        this.fileSize = ch.size();

        buf.flip();
        ensureBytes(CHUNK_HEADER_SIZE);
        if (!readChunk(0)) {
            throw new IOException("Incomplete JFR file");
        }
    }

    public JfrReader(ByteBuffer buf) throws IOException {
        this.ch = null;
        this.buf = buf;
        this.fileSize = buf.limit();

        buf.order(ByteOrder.BIG_ENDIAN);
        if (!readChunk(0)) {
            throw new IOException("Incomplete JFR file");
        }
    }

    @Override
    public void close() throws IOException {
        if (ch != null) {
            ch.close();
        }
    }

    public boolean eof() {
        return state >= STATE_EOF;
    }

    public boolean incomplete() {
        return state == STATE_INCOMPLETE;
    }

    public long durationNanos() {
        return endNanos - startNanos;
    }

    public <E extends Event> void registerEvent(String name, Class<E> eventClass) {
        JfrClass type = typesByName.get(name);
        if (type != null) {
            try {
                customEvents.put(type.id, eventClass.getConstructor(JfrReader.class));
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("No suitable constructor found");
            }
        }
    }

    // Similar to eof(), but parses the next chunk header
    public boolean hasMoreChunks() throws IOException {
        return state == STATE_NEW_CHUNK ? readChunk(buf.position()) : state == STATE_READING;
    }

    public List<Event> readAllEvents() throws IOException {
        return readAllEvents(null);
    }

    public <E extends Event> List<E> readAllEvents(Class<E> cls) throws IOException {
        ArrayList<E> events = new ArrayList<>();
        for (E event; (event = readEvent(cls)) != null; ) {
            events.add(event);
        }
        Collections.sort(events);
        return events;
    }

    public Event readEvent() throws IOException {
        return readEvent(null);
    }

    @SuppressWarnings("unchecked")
    public <E extends Event> E readEvent(Class<E> cls) throws IOException {
        while (ensureBytes(CHUNK_HEADER_SIZE)) {
            int pos = buf.position();
            int size = getVarint();
            int type = getVarint();

            if (type == 'L' && buf.getInt(pos) == CHUNK_SIGNATURE) {
                if (state != STATE_NEW_CHUNK && stopAtNewChunk) {
                    buf.position(pos);
                    state = STATE_NEW_CHUNK;
                } else if (readChunk(pos)) {
                    continue;
                }
                return null;
            }

            if (type == executionSample || type == nativeMethodSample) {
                if (cls == null || cls == ExecutionSample.class) return (E) readExecutionSample();
            } else if (type == allocationInNewTLAB) {
                if (cls == null || cls == AllocationSample.class) return (E) readAllocationSample(true);
            } else if (type == allocationOutsideTLAB || type == allocationSample) {
                if (cls == null || cls == AllocationSample.class) return (E) readAllocationSample(false);
            } else if (type == liveObject) {
                if (cls == null || cls == LiveObject.class) return (E) readLiveObject();
            } else if (type == monitorEnter) {
                if (cls == null || cls == ContendedLock.class) return (E) readContendedLock(false);
            } else if (type == threadPark) {
                if (cls == null || cls == ContendedLock.class) return (E) readContendedLock(true);
            } else if (type == activeSetting) {
                readActiveSetting();
            } else {
                Constructor<? extends Event> customEvent = customEvents.get(type);
                if (customEvent != null && (cls == null || cls == customEvent.getDeclaringClass())) {
                    try {
                        return (E) customEvent.newInstance(this);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    } finally {
                        seek(filePosition + pos + size);
                    }
                }
            }

            seek(filePosition + pos + size);
        }

        state = STATE_EOF;
        return null;
    }

    @SuppressWarnings("unchecked")
    public EventPair readEventWithType() throws IOException {
        while (ensureBytes(CHUNK_HEADER_SIZE)) {
            int pos = buf.position();
            int size = getVarint();
            int type = getVarint();

            if (type == 'L' && buf.getInt(pos) == CHUNK_SIGNATURE) {
                if (state != STATE_NEW_CHUNK && stopAtNewChunk) {
                    buf.position(pos);
                    state = STATE_NEW_CHUNK;
                } else if (readChunk(pos)) {
                    continue;
                }
                return null;
            }

            if (type == executionSample || type == nativeMethodSample) {
                return new EventPair(JfrEventType.EXECUTION_SAMPLE, readExecutionSample());
            } else if (type == allocationInNewTLAB) {
                return new EventPair(JfrEventType.OBJECT_ALLOCATION_IN_NEW_TLAB, readAllocationSample(true));
            } else if (type == allocationOutsideTLAB || type == allocationSample) {
                return new EventPair(JfrEventType.OBJECT_ALLOCATION_OUTSIDE_TLAB, readAllocationSample(false));
            } else if (type == liveObject) {
                return new EventPair(JfrEventType.PROFILER_LIVE_OBJECT, readLiveObject());
            } else if (type == monitorEnter) {
                return new EventPair(JfrEventType.JAVA_MONITOR_ENTER, readContendedLock(false));
            } else if (type == threadPark) {
                return new EventPair(JfrEventType.THREAD_PARK, readContendedLock(true));
            } else if (type == activeSetting) {
                readActiveSetting();
            }

            seek(filePosition + pos + size);
        }

        state = STATE_EOF;
        return null;
    }

    public EventPair readEventWithType1() throws IOException {
        return new EventPair(JfrEventType.EXECUTION_SAMPLE, readEvent());
    }

    private ExecutionSample readExecutionSample() {
        long time = getVarlong();
        int tid = getVarint();
        int stackTraceId = getVarint();
        int threadState = getVarint();
        int contextId = getVarint();
        return new ExecutionSample(time, tid, stackTraceId, threadState);
    }

    private AllocationSample readAllocationSample(boolean tlab) {
        long time = getVarlong();
        int tid = getVarint();
        int stackTraceId = getVarint();
        int classId = getVarint();
        long allocationSize = getVarlong();
        long tlabSize = tlab ? getVarlong() : 0;
        int contextId = getVarint();
        return new AllocationSample(time, tid, stackTraceId, classId, allocationSize, tlabSize);
    }

    private LiveObject readLiveObject() {
        long time = getVarlong();
        int tid = getVarint();
        int stackTraceId = getVarint();
        int classId = getVarint();
        long allocationSize = getVarlong();
        long allocatimeTime = getVarlong();
        return new LiveObject(time, tid, stackTraceId, classId, allocationSize, allocatimeTime);
    }

    private ContendedLock readContendedLock(boolean hasTimeout) {
        long time = getVarlong();
        long duration = getVarlong();
        int tid = getVarint();
        int stackTraceId = getVarint();
        int classId = getVarint();
        if (hasTimeout) getVarlong();
        long until = getVarlong();
        long address = getVarlong();
        if (!hasTimeout) getVarint();
        return new ContendedLock(time, tid, stackTraceId, duration, classId);
    }

    private void readActiveSetting() {
        for (JfrField field : typesByName.get("jdk.ActiveSetting").fields) {
            getVarlong();
            if ("id".equals(field.name)) {
                break;
            }
        }
        String name = getString();
        String value = getString();
        settings.put(name, value);
    }

    private boolean readChunk(int pos) throws IOException {
        if (pos + CHUNK_HEADER_SIZE > buf.limit() || buf.getInt(pos) != CHUNK_SIGNATURE) {
            throw new IOException("Not a valid JFR file");
        }

        int version = buf.getInt(pos + 4);
        if (version < 0x20000 || version > 0x2ffff) {
            throw new IOException("Unsupported JFR version: " + (version >>> 16) + "." + (version & 0xffff));
        }

        long chunkStart = filePosition + pos;
        long chunkSize = buf.getLong(pos + 8);
        if (chunkStart + chunkSize > fileSize) {
            state = STATE_INCOMPLETE;
            return false;
        }

        long cpOffset = buf.getLong(pos + 16);
        long metaOffset = buf.getLong(pos + 24);
        if (cpOffset == 0 || metaOffset == 0) {
            state = STATE_INCOMPLETE;
            return false;
        }

        chunkStartNanos = buf.getLong(pos + 32);
        chunkEndNanos = buf.getLong(pos + 32) + buf.getLong(pos + 40);
        chunkStartTicks = buf.getLong(pos + 48);
        ticksPerSec = buf.getLong(pos + 56);

        startNanos = Math.min(startNanos, chunkStartNanos);
        endNanos = Math.max(endNanos, chunkEndNanos);
        startTicks = Math.min(startTicks, chunkStartTicks);

        types.clear();
        typesByName.clear();

        readMeta(chunkStart + metaOffset);
        readConstantPool(chunkStart + cpOffset);
        cacheEventTypes();

        seek(chunkStart + CHUNK_HEADER_SIZE);
        state = STATE_READING;
        return true;
    }

    private void readMeta(long metaOffset) throws IOException {
        seek(metaOffset);
        ensureBytes(5);

        int posBeforeSize = buf.position();
        ensureBytes(getVarint() - (buf.position() - posBeforeSize));
        getVarint();
        getVarlong();
        getVarlong();
        getVarlong();

        String[] strings = new String[getVarint()];
        for (int i = 0; i < strings.length; i++) {
            strings[i] = getString();
        }
        readElement(strings);
    }

    private Element readElement(String[] strings) {
        String name = strings[getVarint()];

        int attributeCount = getVarint();
        Map<String, String> attributes = new HashMap<>(attributeCount);
        for (int i = 0; i < attributeCount; i++) {
            attributes.put(strings[getVarint()], strings[getVarint()]);
        }

        Element e = createElement(name, attributes);
        int childCount = getVarint();
        for (int i = 0; i < childCount; i++) {
            e.addChild(readElement(strings));
        }
        return e;
    }

    private Element createElement(String name, Map<String, String> attributes) {
        switch (name) {
            case "class": {
                JfrClass type = new JfrClass(attributes);
                if (!attributes.containsKey("superType")) {
                    types.put(type.id, type);
                }
                typesByName.put(type.name, type);
                return type;
            }
            case "field":
                return new JfrField(attributes);
            default:
                return new Element();
        }
    }

    private void readConstantPool(long cpOffset) throws IOException {
        long delta;
        do {
            seek(cpOffset);
            ensureBytes(5);

            int posBeforeSize = buf.position();
            ensureBytes(getVarint() - (buf.position() - posBeforeSize));
            getVarint();
            getVarlong();
            getVarlong();
            delta = getVarlong();
            getVarint();

            int poolCount = getVarint();
            for (int i = 0; i < poolCount; i++) {
                int type = getVarint();
                readConstants(types.get(type));
            }
        } while (delta != 0 && (cpOffset += delta) > 0);
    }

    private void readConstants(JfrClass type) {
        switch (type.name) {
            case "jdk.types.ChunkHeader":
                buf.position(buf.position() + (CHUNK_HEADER_SIZE + 3));
                break;
            case "java.lang.Thread":
                readThreads(type.fields.size());
                break;
            case "java.lang.Class":
                readClasses(type.fields.size());
                break;
            case "java.lang.String":
                readStrings();
                break;
            case "jdk.types.Symbol":
                readSymbols();
                break;
            case "jdk.types.Method":
                readMethods();
                break;
            case "jdk.types.StackTrace":
                readStackTraces();
                break;
            default:
                if (type.simpleType && type.fields.size() == 1) {
                    readEnumValues(type.name);
                } else {
                    readOtherConstants(type.fields);
                }
        }
    }

    private void readThreads(int fieldCount) {
        int count = threads.preallocate(getVarint());
        for (int i = 0; i < count; i++) {
            long id = getVarlong();
            String osName = getString();
            int osThreadId = getVarint();
            String javaName = getString();
            long javaThreadId = getVarlong();
            readFields(fieldCount - 4);
            threads.put(id, javaName != null ? javaName : osName);
        }
    }

    private void readClasses(int fieldCount) {
        int count = classes.preallocate(getVarint());
        for (int i = 0; i < count; i++) {
            long id = getVarlong();
            long loader = getVarlong();
            long name = getVarlong();
            long pkg = getVarlong();
            int modifiers = getVarint();
            readFields(fieldCount - 4);
            classes.put(id, new ClassRef(name));
        }
    }

    private void readMethods() {
        int count = methods.preallocate(getVarint());
        for (int i = 0; i < count; i++) {
            long id = getVarlong();
            long cls = getVarlong();
            long name = getVarlong();
            long sig = getVarlong();
            int modifiers = getVarint();
            int hidden = getVarint();
            methods.put(id, new MethodRef(cls, name, sig));
        }
    }

    private void readStackTraces() {
        int count = stackTraces.preallocate(getVarint());
        for (int i = 0; i < count; i++) {
            long id = getVarlong();
            int truncated = getVarint();
            StackTrace stackTrace = readStackTrace();
            stackTraces.put(id, stackTrace);
        }
    }

    private StackTrace readStackTrace() {
        int depth = getVarint();
        long[] methods = new long[depth];
        byte[] types = new byte[depth];
        int[] locations = new int[depth];
        for (int i = 0; i < depth; i++) {
            methods[i] = getVarlong();
            int line = getVarint();
            int bci = getVarint();
            locations[i] = line << 16 | (bci & 0xffff);
            types[i] = buf.get();
        }
        return new StackTrace(methods, types, locations);
    }

    private void readStrings() {
        int count = strings.preallocate(getVarint());
        for (int i = 0; i < count; i++) {
            strings.put(getVarlong(), getString());
        }
    }

    private void readSymbols() {
        int count = symbols.preallocate(getVarint());
        for (int i = 0; i < count; i++) {
            long id = getVarlong();
            if (buf.get() != 3) {
                throw new IllegalArgumentException("Invalid symbol encoding");
            }
            symbols.put(id, getBytes());
        }
    }

    private void readEnumValues(String typeName) {
        HashMap<Integer, String> map = new HashMap<>();
        int count = getVarint();
        for (int i = 0; i < count; i++) {
            map.put((int) getVarlong(), getString());
        }
        enums.put(typeName, map);
    }

    private void readOtherConstants(List<JfrField> fields) {
        int stringType = getTypeId("java.lang.String");

        boolean[] numeric = new boolean[fields.size()];
        for (int i = 0; i < numeric.length; i++) {
            JfrField f = fields.get(i);
            numeric[i] = f.constantPool || f.type != stringType;
        }

        int count = getVarint();
        for (int i = 0; i < count; i++) {
            getVarlong();
            readFields(numeric);
        }
    }

    private void readFields(boolean[] numeric) {
        for (boolean n : numeric) {
            if (n) {
                getVarlong();
            } else {
                getString();
            }
        }
    }

    private void readFields(int count) {
        while (count-- > 0) {
            getVarlong();
        }
    }

    private void cacheEventTypes() {
        executionSample = getTypeId("jdk.ExecutionSample");
        nativeMethodSample = getTypeId("jdk.NativeMethodSample");
        allocationInNewTLAB = getTypeId("jdk.ObjectAllocationInNewTLAB");
        allocationOutsideTLAB = getTypeId("jdk.ObjectAllocationOutsideTLAB");
        allocationSample = getTypeId("jdk.ObjectAllocationSample");
        liveObject = getTypeId("profiler.LiveObject");
        monitorEnter = getTypeId("jdk.JavaMonitorEnter");
        threadPark = getTypeId("jdk.ThreadPark");
        activeSetting = getTypeId("jdk.ActiveSetting");

        registerEvent("jdk.CPULoad", CPULoad.class);
        registerEvent("jdk.GCHeapSummary", GCHeapSummary.class);
        registerEvent("jdk.ObjectCount", ObjectCount.class);
        registerEvent("jdk.ObjectCountAfterGC", ObjectCount.class);
    }

    private int getTypeId(String typeName) {
        JfrClass type = typesByName.get(typeName);
        return type != null ? type.id : -1;
    }

    public int getEnumKey(String typeName, String value) {
        Map<Integer, String> enumValues = enums.get(typeName);
        if (enumValues != null) {
            for (Map.Entry<Integer, String> entry : enumValues.entrySet()) {
                if (value.equals(entry.getValue())) {
                    return entry.getKey();
                }
            }
        }
        return -1;
    }

    public String getEnumValue(String typeName, int key) {
        return enums.get(typeName).get(key);
    }

    public int getVarint() {
        int result = 0;
        for (int shift = 0; ; shift += 7) {
            byte b = buf.get();
            result |= (b & 0x7f) << shift;
            if (b >= 0) {
                return result;
            }
        }
    }

    public long getVarlong() {
        long result = 0;
        for (int shift = 0; shift < 56; shift += 7) {
            byte b = buf.get();
            result |= (b & 0x7fL) << shift;
            if (b >= 0) {
                return result;
            }
        }
        return result | (buf.get() & 0xffL) << 56;
    }

    public float getFloat() {
        return buf.getFloat();
    }

    public double getDouble() {
        return buf.getDouble();
    }

    public String getString() {
        switch (buf.get()) {
            case 0:
                return null;
            case 1:
                return "";
            case 2:
                return strings.get(getVarlong());
            case 3:
                return new String(getBytes(), StandardCharsets.UTF_8);
            case 4: {
                char[] chars = new char[getVarint()];
                for (int i = 0; i < chars.length; i++) {
                    chars[i] = (char) getVarint();
                }
                return new String(chars);
            }
            case 5:
                return new String(getBytes(), StandardCharsets.ISO_8859_1);
            default:
                throw new IllegalArgumentException("Invalid string encoding");
        }
    }

    public byte[] getBytes() {
        byte[] bytes = new byte[getVarint()];
        buf.get(bytes);
        return bytes;
    }

    private void seek(long pos) throws IOException {
        long bufPosition = pos - filePosition;
        if (bufPosition >= 0 && bufPosition <= buf.limit()) {
            buf.position((int) bufPosition);
        } else {
            filePosition = pos;
            ch.position(pos);
            buf.rewind().flip();
        }
    }

    private boolean ensureBytes(int needed) throws IOException {
        if (buf.remaining() >= needed) {
            return true;
        }

        if (ch == null) {
            return false;
        }

        filePosition += buf.position();

        if (buf.capacity() < needed) {
            ByteBuffer newBuf = ByteBuffer.allocateDirect(needed);
            newBuf.put(buf);
            buf = newBuf;
        } else {
            buf.compact();
        }

        while (ch.read(buf) > 0 && buf.position() < needed) {
            // keep reading
        }
        buf.flip();
        return buf.limit() > 0;
    }
}
