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

package org.apache.skywalking.oap.server.core.dsl;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.AttributeInfo;
import javassist.bytecode.ClassFile;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.LocalVariableAttribute;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.SourceFileAttribute;
import lombok.extern.slf4j.Slf4j;

/**
 * Emits the artifacts of a generated DSL class: the {@code .class}, the {@code .java} beside it,
 * and the bytecode attributes that let a stack frame resolve back to source.
 *
 * <p>Separate from {@link DslSourceRef}, which only DESCRIBES coordinates. Producing a name and
 * performing I/O with it are different jobs, and keeping them apart is what lets the coordinate
 * model be a plain value object.
 *
 * <p>Shared by OAL, MAL, LAL and Hierarchy. Each used to carry its own copy, and they had drifted:
 * two different {@code SourceFile} semantics, a source-file header that existed in two versions,
 * and a class writer that in one DSL did not create its own directory.
 */
@Slf4j
public final class DslGeneratedFileWriter {

    private DslGeneratedFileWriter() {
    }

    /** Lines the {@link #writeSourceFile} header occupies before the caller's source begins. */
    public static final int SOURCE_FILE_PREAMBLE_LINES = 4;

    /**
     * Writes the generated {@code .class} beside its sourceFile.
     *
     * <p>Creates the directory itself: the generated source file and the class file are written independently,
     * and whichever runs first must not depend on the other having made the directory.
     *
     * @param outputDir destination, or null to skip
     * @param ctClass   the generated class
     */
    public static void writeClassFile(final File outputDir, final CtClass ctClass) {
        if (outputDir == null) {
            return;
        }
        if (!outputDir.exists() && !outputDir.mkdirs() && !outputDir.exists()) {
            log.warn("Could not create DSL class output dir {}", outputDir);
            return;
        }
        final File file = new File(outputDir, ctClass.getSimpleName() + ".class");
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            ctClass.toBytecode(out);
        } catch (Exception e) {
            log.warn("Failed to write class file {}: {}", file, e.getMessage());
        }
    }

    /**
     * Writes the synthetic source beside the {@code .class}. UTF-8 with an ASCII header, so the
     * file decodes identically wherever the build ran.
     *
     * @param outputDir           destination, or null to skip
     * @param generatedSimpleName simple name of the generated class
     * @param javaSource          the exact text handed to Javassist
     */
    public static void writeSourceFile(final File outputDir,
                                    final String generatedSimpleName,
                                    final String javaSource) {
        if (outputDir == null || javaSource == null) {
            return;
        }
        if (!outputDir.exists() && !outputDir.mkdirs() && !outputDir.exists()) {
            log.warn("Could not create DSL class output dir {}", outputDir);
            return;
        }
        final File file = new File(outputDir, generatedSimpleName + ".java");
        try (Writer w = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write("// Synthetic source - Javassist compile input for ");
            w.write(generatedSimpleName);
            w.write("\n// Written when SW_DYNAMIC_CLASS_ENGINE_DEBUG is on; used by IDE\n");
            w.write("// source-attach to render the bytecode without FernFlower.\n\n");
            w.write(javaSource);
            if (!javaSource.endsWith("\n")) {
                w.write("\n");
            }
        } catch (Exception e) {
            log.warn("Failed to write DSL source file {}: {}", file, e.getMessage());
        }
    }

    /**
     * The 1-based line at which {@code signatureMarker} appears in the written sourceFile.
     *
     * <p>Read from the assembled text rather than a hardcoded envelope constant: the envelope
     * varies per rule, so a constant drifts silently the moment codegen changes shape.
     *
     * @param javaSource      the same text passed to {@link #writeSourceFile}
     * @param methodName      the method whose declaration line is wanted
     * @return 1-based line in the written file, or {@link DslSourceRef#UNRESOLVED}
     */
    public static int lineOfMethod(final String javaSource, final String methodName) {
        if (javaSource == null || methodName == null) {
            return DslSourceRef.UNRESOLVED;
        }
        // Match a DECLARATION line, not any occurrence of the name. LAL embeds the verbatim DSL
        // and generated string literals ahead of execute(), so a bare indexOf can land inside a
        // regex or a quoted rule body and report a line that is real but not the method's.
        final String[] lines = javaSource.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            final String trimmed = lines[i].trim();
            if (!declaresMethod(trimmed, methodName)) {
                continue;
            }
            if (!trimmed.startsWith("public ") && !trimmed.startsWith("private ")
                && !trimmed.startsWith("protected ") && !trimmed.startsWith("static ")) {
                continue;
            }
            if (!trimmed.endsWith("{") && !trimmed.endsWith(")")) {
                continue;
            }
            return SOURCE_FILE_PREAMBLE_LINES + 1 + i;
        }
        return DslSourceRef.UNRESOLVED;
    }

    /**
     * Whether the line names exactly {@code methodName}: the name immediately followed by
     * {@code '('} and not preceded by an identifier character.
     *
     * <p>A bare {@code contains(methodName + "(")} also accepts a LONGER method whose name ends
     * with the wanted one — {@code serialize} inside {@code deserialize(}, and OAL declares both
     * on every metrics class. That resolved correctly only because the shorter one happened to be
     * emitted first; reordering the template list, or an operator writing {@code cpm} beside
     * {@code commando_cpm} in a {@code .oal} file, silently stamped one method with another's line.
     *
     * @param trimmed    a whitespace-trimmed source line
     * @param methodName the method whose declaration is wanted
     * @return true when the line names that exact method
     */
    private static boolean declaresMethod(final String trimmed, final String methodName) {
        final String needle = methodName + "(";
        for (int at = trimmed.indexOf(needle); at >= 0; at = trimmed.indexOf(needle, at + 1)) {
            if (at == 0 || !Character.isJavaIdentifierPart(trimmed.charAt(at - 1))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Overwrites the {@code SourceFile} Javassist installs by default.
     *
     * @param ctClass the generated class
     * @param value   the attribute value, from {@link DslSourceRef#sourceFileOf(String)}
     */
    public static void setSourceFile(final CtClass ctClass, final String value) {
        try {
            final ClassFile cf = ctClass.getClassFile();
            cf.addAttribute(new SourceFileAttribute(cf.getConstPool(), value));
        } catch (Exception e) {
            log.warn("Failed to set SourceFile on {}: {}", ctClass.getName(), e.getMessage());
        }
    }

    /**
     * Attaches a one-entry {@code LineNumberTable} pointing the method at its signature.
     *
     * @param method      the generated method
     * @param lineInClass line the signature occupies in the sourceFile; non-positive omits the
     *                    attribute, since {@code line_number} is an unsigned {@code u2}
     */
    public static void attachSignatureLine(final CtMethod method, final int lineInClass) {
        if (method == null || lineInClass <= 0) {
            return;
        }
        try {
            final MethodInfo mi = method.getMethodInfo();
            final CodeAttribute code = mi.getCodeAttribute();
            if (code == null) {
                return;
            }
            code.getAttributes().add(new AttributeInfo(mi.getConstPool(), "LineNumberTable",
                new byte[]{0, 1, 0, 0, (byte) (lineInClass >> 8), (byte) lineInClass}));
        } catch (Exception e) {
            log.warn("Failed to attach LineNumberTable: {}", e.getMessage());
        }
    }

    /**
     * Adds a {@code LocalVariableTable} so a debugger shows named variables rather than
     * {@code var0}, {@code var1}.
     *
     * @param method    the generated method
     * @param className owning class, for the {@code this} slot descriptor
     * @param vars      {@code {name, descriptor}} pairs for slots after {@code this}
     */
    public static void addLocalVariableTable(final CtMethod method,
                                             final String className,
                                             final String[][] vars) {
        try {
            final MethodInfo mi = method.getMethodInfo();
            final CodeAttribute code = mi.getCodeAttribute();
            if (code == null) {
                return;
            }
            final ConstPool cp = mi.getConstPool();
            final int len = code.getCodeLength();
            final LocalVariableAttribute lvt = new LocalVariableAttribute(cp);
            lvt.addEntry(0, len, cp.addUtf8Info("this"),
                cp.addUtf8Info("L" + className.replace('.', '/') + ";"), 0);
            for (int i = 0; i < vars.length; i++) {
                lvt.addEntry(0, len, cp.addUtf8Info(vars[i][0]),
                    cp.addUtf8Info(vars[i][1]), i + 1);
            }
            code.getAttributes().add(lvt);
        } catch (Exception e) {
            log.warn("Failed to add LocalVariableTable: {}", e.getMessage());
        }
    }
}
