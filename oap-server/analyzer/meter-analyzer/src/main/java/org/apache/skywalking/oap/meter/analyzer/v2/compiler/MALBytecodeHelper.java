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
 */

package org.apache.skywalking.oap.meter.analyzer.v2.compiler;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javassist.CtClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Javassist bytecode utilities for MAL-generated classes.
 *
 * <p>Handles:
 * <ul>
 *   <li>Class naming: builds names from YAML source + rule name + dedup suffix</li>
 *   <li>Debug output: writes {@code .class} files when
 *       {@code SW_DYNAMIC_CLASS_ENGINE_DEBUG} is set</li>
 *   <li>Bytecode attributes: {@code LineNumberTable} and {@code LocalVariableTable}
 *       for meaningful stack traces</li>
 * </ul>
 */
@Slf4j
final class MALBytecodeHelper {

    static final String PACKAGE_PREFIX =
        "org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt.";

    private static final AtomicInteger CLASS_COUNTER = new AtomicInteger(0);

    private static final Set<String> USED_CLASS_NAMES =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private File classOutputDir;
    private String classNameHint;
    private String yamlSource;

    void setClassOutputDir(final File dir) {
        this.classOutputDir = dir;
    }

    void setClassNameHint(final String hint) {
        this.classNameHint = hint;
    }

    String getClassNameHint() {
        return classNameHint;
    }

    void setYamlSource(final String yamlSource) {
        this.yamlSource = yamlSource;
    }

    // ==================== Class naming ====================

    /**
     * Builds FQCN for a generated class.
     *
     * <p>When {@code classNameHint} is set (e.g. from YAML rule name), produces:
     * {@code ...rt.vm_L25_cpu_total_percentage}. Otherwise falls back to
     * {@code ...rt.MalExpr_0}, {@code ...rt.MalFilter_1}, etc.
     */
    String makeClassName(final String defaultPrefix) {
        if (classNameHint != null) {
            return dedupClassName(PACKAGE_PREFIX + buildHintedName());
        }
        return PACKAGE_PREFIX + defaultPrefix + CLASS_COUNTER.getAndIncrement();
    }

    private String buildHintedName() {
        final String hint = MALCodegenHelper.sanitizeName(classNameHint);
        if (yamlSource == null) {
            return hint;
        }
        String yamlBase = yamlSource;
        String lineNo = null;
        final int colonIdx = yamlSource.lastIndexOf(':');
        if (colonIdx > 0) {
            yamlBase = yamlSource.substring(0, colonIdx);
            lineNo = yamlSource.substring(colonIdx + 1);
        }
        final int dotIdx = yamlBase.lastIndexOf('.');
        if (dotIdx > 0) {
            yamlBase = yamlBase.substring(0, dotIdx);
        }
        final StringBuilder sb = new StringBuilder();
        sb.append(MALCodegenHelper.sanitizeName(yamlBase));
        if (lineNo != null) {
            sb.append("_L").append(lineNo);
        }
        sb.append('_').append(hint);
        return sb.toString();
    }

    private String dedupClassName(final String base) {
        if (USED_CLASS_NAMES.add(base)) {
            return base;
        }
        for (int i = 2; ; i++) {
            final String candidate = base + "_" + i;
            if (USED_CLASS_NAMES.add(candidate)) {
                return candidate;
            }
        }
    }

    // ==================== Debug output ====================

    /**
     * Builds the SourceFile name for a generated class.
     * Example: {@code "(vm.yaml:25)cpu_total.java"}
     */
    String formatSourceFileName(final String metricName) {
        final String classFile = metricName + ".java";
        if (yamlSource != null) {
            return "(" + yamlSource + ")" + classFile;
        }
        return classFile;
    }

    /**
     * Sets the {@code SourceFile} attribute so stack traces show the metric name.
     */
    static void setSourceFile(final CtClass ctClass, final String name) {
        try {
            final javassist.bytecode.ClassFile cf = ctClass.getClassFile();
            final javassist.bytecode.AttributeInfo sf =
                cf.getAttribute("SourceFile");
            if (sf != null) {
                final javassist.bytecode.ConstPool cp = cf.getConstPool();
                final int idx = cp.addUtf8Info(name);
                sf.set(new byte[]{(byte) (idx >> 8), (byte) idx});
            }
        } catch (Exception e) {
            // best-effort
        }
    }

    /**
     * Writes a {@code .class} file for debugging when {@code classOutputDir} is set.
     */
    void writeClassFile(final CtClass ctClass) {
        if (classOutputDir == null) {
            return;
        }
        if (!classOutputDir.exists()) {
            classOutputDir.mkdirs();
        }
        final File file = new File(
            classOutputDir, ctClass.getSimpleName() + ".class");
        try (DataOutputStream out =
                 new DataOutputStream(new FileOutputStream(file))) {
            ctClass.toBytecode(out);
        } catch (Exception e) {
            log.warn("Failed to write class file {}: {}",
                     file, e.getMessage(), e);
        }
    }

    // ==================== Bytecode attributes ====================

    /**
     * Adds a {@code LineNumberTable} attribute to the method.
     * Scans bytecode for store instructions to local variable slots
     * >= {@code firstResultSlot}, assigning sequential line numbers.
     */
    void addLineNumberTable(final javassist.CtMethod method,
                             final int firstResultSlot) {
        try {
            final javassist.bytecode.MethodInfo mi = method.getMethodInfo();
            final javassist.bytecode.CodeAttribute code =
                mi.getCodeAttribute();
            if (code == null) {
                return;
            }
            final List<int[]> entries = new ArrayList<>();
            int line = 1;
            boolean nextIsNewLine = true;

            final javassist.bytecode.CodeIterator ci = code.iterator();
            while (ci.hasNext()) {
                final int pc = ci.next();
                if (nextIsNewLine) {
                    entries.add(new int[]{pc, line++});
                    nextIsNewLine = false;
                }
                final int op = ci.byteAt(pc) & 0xFF;
                int slot = -1;
                if (op >= 59 && op <= 78) {
                    slot = (op - 59) % 4;
                } else if (op >= 54 && op <= 58) {
                    slot = ci.byteAt(pc + 1) & 0xFF;
                }
                if (slot >= firstResultSlot) {
                    nextIsNewLine = true;
                }
            }

            if (entries.isEmpty()) {
                return;
            }
            final javassist.bytecode.ConstPool cp = mi.getConstPool();
            final byte[] info = new byte[2 + entries.size() * 4];
            info[0] = (byte) (entries.size() >> 8);
            info[1] = (byte) entries.size();
            for (int i = 0; i < entries.size(); i++) {
                final int off = 2 + i * 4;
                info[off] = (byte) (entries.get(i)[0] >> 8);
                info[off + 1] = (byte) entries.get(i)[0];
                info[off + 2] = (byte) (entries.get(i)[1] >> 8);
                info[off + 3] = (byte) entries.get(i)[1];
            }
            code.getAttributes().add(
                new javassist.bytecode.AttributeInfo(
                    cp, "LineNumberTable", info));
        } catch (Exception e) {
            log.warn("Failed to add LineNumberTable: {}", e.getMessage());
        }
    }

    /**
     * Adds a {@code LocalVariableTable} attribute for debug info.
     */
    void addLocalVariableTable(final javassist.CtMethod method,
                                final String className,
                                final String[][] vars) {
        try {
            final javassist.bytecode.MethodInfo mi = method.getMethodInfo();
            final javassist.bytecode.CodeAttribute code =
                mi.getCodeAttribute();
            if (code == null) {
                return;
            }
            final javassist.bytecode.ConstPool cp = mi.getConstPool();
            final int len = code.getCodeLength();

            final javassist.bytecode.LocalVariableAttribute lva =
                new javassist.bytecode.LocalVariableAttribute(cp);
            int slot = 0;
            lva.addEntry(0, len,
                cp.addUtf8Info("this"),
                cp.addUtf8Info(
                    "L" + className.replace('.', '/') + ";"),
                slot++);
            for (final String[] var : vars) {
                lva.addEntry(0, len,
                    cp.addUtf8Info(var[0]),
                    cp.addUtf8Info(var[1]), slot++);
            }
            code.getAttributes().add(lva);
        } catch (Exception e) {
            log.warn("Failed to add LocalVariableTable: {}",
                     e.getMessage());
        }
    }

    /**
     * Adds {@code LocalVariableTable} for the {@code run(Map)} method,
     * including the {@code sf} variable and any temp variables ({@code _t0, _t1, ...}).
     */
    void addRunLocalVariableTable(final javassist.CtMethod method,
                                   final String className,
                                   final int tempCount) {
        final String sfDesc =
            "L" + MALCodegenHelper.SF.replace('.', '/') + ";";
        final String[][] vars = new String[2 + tempCount][];
        vars[0] = new String[]{"samples", "Ljava/util/Map;"};
        vars[1] = new String[]{MALCodegenHelper.RUN_VAR, sfDesc};
        for (int i = 0; i < tempCount; i++) {
            vars[2 + i] = new String[]{"_t" + i, sfDesc};
        }
        addLocalVariableTable(method, className, vars);
    }
}
