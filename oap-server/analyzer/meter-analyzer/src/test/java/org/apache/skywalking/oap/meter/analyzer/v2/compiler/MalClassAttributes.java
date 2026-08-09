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

package org.apache.skywalking.oap.meter.analyzer.v2.compiler;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javassist.bytecode.ClassFile;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.LineNumberAttribute;
import javassist.bytecode.MethodInfo;

/**
 * Reads debug attributes back out of a generated {@code .class}.
 *
 * <p>Asserting on the emitted bytecode rather than on the codegen's inputs is the point: the
 * inputs were correct before this change too, and the attribute was still wrong.
 */
final class MalClassAttributes {

    private MalClassAttributes() {
    }

    /**
     * @param classFile a generated class file on disk
     * @return every {@code LineNumberTable} entry as {@code {start_pc, line_number}}, across all
     *         methods; empty when the class carries no table at all
     * @throws IOException if the class file cannot be read
     */
    static List<int[]> lineNumberTableOf(final File classFile) throws IOException {
        final List<int[]> out = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(classFile.toPath())))) {
            final ClassFile cf = new ClassFile(in);
            for (final MethodInfo mi : cf.getMethods()) {
                final CodeAttribute code = mi.getCodeAttribute();
                if (code == null) {
                    continue;
                }
                final LineNumberAttribute lna =
                    (LineNumberAttribute) code.getAttribute(LineNumberAttribute.tag);
                if (lna == null) {
                    continue;
                }
                for (int i = 0; i < lna.tableLength(); i++) {
                    out.add(new int[]{lna.startPc(i), lna.lineNumber(i)});
                }
            }
        }
        return out;
    }

    /**
     * @param classFile a generated class file on disk
     * @return the {@code SourceFile} attribute value, or {@code null} when absent
     * @throws IOException if the class file cannot be read
     */
    static String sourceFileOf(final File classFile) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(classFile.toPath())))) {
            return new ClassFile(in).getSourceFile();
        }
    }
}
