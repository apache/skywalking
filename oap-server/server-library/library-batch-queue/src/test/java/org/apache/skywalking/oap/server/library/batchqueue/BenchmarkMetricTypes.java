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

package org.apache.skywalking.oap.server.library.batchqueue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * 2000 distinct metric subclasses generated at class-load time for benchmark
 * handler-map dispatch testing. Each class represents a distinct metric type
 * that gets routed to its own handler via {@code data.getClass().hashCode()}.
 *
 * <p>Classes are generated as bytecode at runtime using
 * {@link MethodHandles.Lookup#defineClass(byte[])} to avoid a 6000+ line
 * source file. Each generated class {@code Dyn0..Dyn1999} extends
 * {@link TypedMetric} with a constructor that calls {@code super(typeId, v)}.
 */
@SuppressWarnings("all")
class BenchmarkMetricTypes {

    static class TypedMetric {
        final int typeId;
        final long value;

        TypedMetric(final int typeId, final long value) {
            this.typeId = typeId;
            this.value = value;
        }
    }

    @FunctionalInterface
    interface MetricFactory {
        TypedMetric create(long value);
    }

    static final int MAX_TYPES = 2000;
    static final Class<? extends TypedMetric>[] CLASSES = new Class[MAX_TYPES];
    static final MetricFactory[] FACTORIES = new MetricFactory[MAX_TYPES];

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final String SUPER_INTERNAL =
        "org/apache/skywalking/oap/server/library/batchqueue/BenchmarkMetricTypes$TypedMetric";

    static {
        try {
            for (int i = 0; i < MAX_TYPES; i++) {
                final String name = "org/apache/skywalking/oap/server/library/batchqueue/Dyn" + i;
                final byte[] bytes = buildClassBytes(name, SUPER_INTERNAL, i);
                final Class<? extends TypedMetric> cls =
                    (Class<? extends TypedMetric>) LOOKUP.defineClass(bytes);
                CLASSES[i] = cls;
                final MethodHandle mh = LOOKUP.findConstructor(
                    cls, MethodType.methodType(void.class, long.class));
                FACTORIES[i] = v -> {
                    try {
                        return (TypedMetric) mh.invoke(v);
                    } catch (final Throwable e) {
                        throw new RuntimeException(e);
                    }
                };
            }
        } catch (final Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Build minimal class bytecode:
     * <pre>
     * class {name} extends TypedMetric {
     *     {name}(long v) { super(typeId, v); }
     * }
     * </pre>
     */
    private static byte[] buildClassBytes(final String thisClass, final String superClass,
                                          final int typeId) throws IOException {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream(256);
        final DataOutputStream out = new DataOutputStream(buf);

        // Magic + version (Java 11 = 55)
        out.writeInt(0xCAFEBABE);
        out.writeShort(0);
        out.writeShort(55);

        // Constant pool (10 entries, 1-indexed => count = 11)
        out.writeShort(11);
        // #1 Methodref -> #3.#7  (superClass.<init>:(IJ)V)
        out.writeByte(10);
        out.writeShort(3);
        out.writeShort(7);
        // #2 Class -> #8  (this class)
        out.writeByte(7);
        out.writeShort(8);
        // #3 Class -> #9  (super class)
        out.writeByte(7);
        out.writeShort(9);
        // #4 Utf8 "<init>"
        out.writeByte(1);
        out.writeUTF("<init>");
        // #5 Utf8 "(J)V"
        out.writeByte(1);
        out.writeUTF("(J)V");
        // #6 Utf8 "Code"
        out.writeByte(1);
        out.writeUTF("Code");
        // #7 NameAndType -> #4:#10  (<init>:(IJ)V)
        out.writeByte(12);
        out.writeShort(4);
        out.writeShort(10);
        // #8 Utf8 this class name
        out.writeByte(1);
        out.writeUTF(thisClass);
        // #9 Utf8 super class name
        out.writeByte(1);
        out.writeUTF(superClass);
        // #10 Utf8 "(IJ)V"
        out.writeByte(1);
        out.writeUTF("(IJ)V");

        // Access flags: ACC_SUPER (0x0020)
        out.writeShort(0x0020);
        // This class (#2), Super class (#3)
        out.writeShort(2);
        out.writeShort(3);
        // Interfaces: 0
        out.writeShort(0);
        // Fields: 0
        out.writeShort(0);

        // Methods: 1 (constructor)
        out.writeShort(1);
        // Method access_flags: 0 (package-private)
        out.writeShort(0);
        // Method name: #4 (<init>)
        out.writeShort(4);
        // Method descriptor: #5 ((J)V)
        out.writeShort(5);
        // Method attributes: 1 (Code)
        out.writeShort(1);

        // Code attribute
        out.writeShort(6); // attribute_name_index -> "Code"

        // Build bytecode first to know length
        final byte[] code = buildConstructorCode(typeId);
        final int codeAttrLen = 2 + 2 + 4 + code.length + 2 + 2; // max_stack + max_locals + code_length + code + exception_table_length + attributes_count
        out.writeInt(codeAttrLen);
        out.writeShort(4); // max_stack (this + int + long[2 slots])
        out.writeShort(3); // max_locals (this + long[2 slots])
        out.writeInt(code.length);
        out.write(code);
        out.writeShort(0); // exception_table_length
        out.writeShort(0); // code attributes_count

        // Class attributes: 0
        out.writeShort(0);

        out.flush();
        return buf.toByteArray();
    }

    /**
     * Constructor bytecode: aload_0, sipush typeId, lload_1, invokespecial #1, return
     */
    private static byte[] buildConstructorCode(final int typeId) {
        final ByteArrayOutputStream code = new ByteArrayOutputStream(16);
        // aload_0
        code.write(0x2A);
        // sipush typeId (works for 0..32767)
        code.write(0x11);
        code.write((typeId >> 8) & 0xFF);
        code.write(typeId & 0xFF);
        // lload_1
        code.write(0x1F);
        // invokespecial #1
        code.write(0xB7);
        code.write(0x00);
        code.write(0x01);
        // return
        code.write(0xB1);
        return code.toByteArray();
    }
}
