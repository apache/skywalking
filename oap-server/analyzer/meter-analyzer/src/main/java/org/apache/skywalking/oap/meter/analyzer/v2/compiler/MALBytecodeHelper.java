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

import org.apache.skywalking.oap.server.core.dsl.DslGeneratedFileWriter;
import org.apache.skywalking.oap.server.core.dsl.DslClassNaming;
import org.apache.skywalking.oap.server.core.dsl.DslSourceRef;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.AttributeInfo;
import javassist.bytecode.ConstPool;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.MethodInfo;
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

    private File classOutputDir;
    private String classNameHint;
    /** Rule anchor, parsed once on the way in so nothing downstream re-splits it. */
    private DslSourceRef ruleAnchor = DslSourceRef.ofRule(null, DslSourceRef.UNRESOLVED);
    /**
     * When true, each apply gets its own per-file classloader, so generated class names are
     * scoped to that loader and don't need the process-wide dedup in {@link org.apache.skywalking.oap.server.core.dsl.DslClassNaming}.
     * Set by {@link MALClassGenerator} when its {@code targetClassLoader} is non-null — the
     * runtime-rule hot-update path. Legacy startup (shared OAP app loader) keeps dedup on.
     */
    private boolean perFileClassLoader;

    void setClassOutputDir(final File dir) {
        this.classOutputDir = dir;
    }

    void setClassNameHint(final String hint) {
        this.classNameHint = hint;
    }

    String getClassNameHint() {
        return classNameHint;
    }

    void setSourceRef(final DslSourceRef ref) {
        this.ruleAnchor = ref == null ? DslSourceRef.ofRule(null, DslSourceRef.UNRESOLVED) : ref;
    }

    /**
     * @param yamlSource the fused {@code "vm.yaml:38"} form, for callers that still hold a string
     * @deprecated pass a {@link DslSourceRef} via {@link #setSourceRef}; the string form exists
     *     only for tests and the offline generator, and parsing it is a self-imposed boundary
     *     between in-process producers and consumers that both depend on server-core.
     */
    @Deprecated
    void setYamlSource(final String yamlSource) {
        this.ruleAnchor = DslSourceRef.parse(yamlSource);
    }

    void setPerFileClassLoader(final boolean perFileClassLoader) {
        this.perFileClassLoader = perFileClassLoader;
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
            // The runtime-rule path gives each apply its own classloader, so identical names are
            // already isolated there and a process-wide dedup set would only grow.
            return DslClassNaming.allocate(
                PACKAGE_PREFIX + DslClassNaming.stem(ruleAnchor, classNameHint),
                !perFileClassLoader);
        }
        return PACKAGE_PREFIX + defaultPrefix + CLASS_COUNTER.getAndIncrement();
    }

    // ==================== Debug output ====================

    /**
     * Builds the {@code SourceFile} name for a generated class.
     *
 * <p>It leads with the RULE, then the generated file name. Equalling the written .java was the
     * earlier contract and is no longer the goal: that file exists only under
     * SW_DYNAMIC_CLASS_ENGINE_DEBUG, so in production it addresses nothing.
     *
     * <p>The value leads with the RULE — {@code (otel-rules/vm.yaml:38)vm_L38_cpu.java} — because
     * the generated source file exists only under SW_DYNAMIC_CLASS_ENGINE_DEBUG, so in production there is no
     * .java on disk for a bare file name to address.
     */
    String sourceFileNameOf(final CtClass ctClass) {
        return ruleAnchor.sourceFileOf(ctClass.getSimpleName());
    }

    /**
     * Writes a {@code .class} file for debugging when {@code classOutputDir} is set.
     */
    void writeClassFile(final CtClass ctClass) {
        DslGeneratedFileWriter.writeClassFile(classOutputDir, ctClass);
    }

    /**
     * Writes the Javassist-input Java source for a generated class as a
     * sibling {@code <ClassName>.java} file when {@code classOutputDir} is
     * set. The {@code SourceFile} attribute on the {@code .class} points at
     * this name, so IDE source-attach renders it directly without going
     * through FernFlower / a decompiler — operators see the EXACT code
     * Javassist compiled. Caller passes pre-formatted Java source built
     * during codegen (the {@code makeClass(...)}/{@code make(...)} input
     * strings concatenated with the public class envelope).
     */
    void writeSourceFile(final CtClass ctClass, final String javaSource) {
        DslGeneratedFileWriter.writeSourceFile(classOutputDir, ctClass.getSimpleName(), javaSource);
    }

    // ==================== Bytecode attributes ====================

    /**
     * Adds a {@code LineNumberTable} mapping each bytecode boundary to its line in the
     * {@code .java} source written for the generated class.
     *
     * <p>Boundaries are the first instruction plus the instruction after every store to a local
     * slot {@code >= firstResultSlot}, which for the variable-per-expression codegen is one per
     * emitted statement. {@code boundaryLines} must list the corresponding method-relative source
     * lines in the same order — {@link MalGeneratedSourceLines} derives them from the same
     * generated text, so the two agree by construction.
     *
     * <p>This previously emitted {@code 1,2,3…} — statement ordinals that matched neither the generated
     * class source nor the rule YAML, so no IDE could resolve a frame.
     *
     * @param method          the generated method
     * @param firstResultSlot lowest local slot that holds a statement result
     * @param statementLines  1-based lines WITHIN THE GENERATED METHOD, in boundary order
     * @param methodSignatureLineInClass line the method signature occupies in the generated
     *                        class source, used to convert method-relative to class-relative
     */
    void addLineNumberTable(final CtMethod method,
                             final int firstResultSlot,
                             final java.util.List<Integer> statementLines,
                             final int methodSignatureLineInClass) {
        try {
            final MethodInfo mi = method.getMethodInfo();
            final CodeAttribute code =
                mi.getCodeAttribute();
            if (code == null) {
                return;
            }
            // No attribution beats false attribution. Since the signature line is now SEARCHED
            // rather than computed, it can come back UNRESOLVED (-1) -- and -1 + lineInMethod - 1
            // is POSITIVE for any statement past the second, so it would sail past the per-entry
            // "<= 0" bail below and emit a confidently wrong table.
            if (methodSignatureLineInClass <= 0) {
                return;
            }
            final List<int[]> entries = new ArrayList<>();
            int boundary = 0;
            boolean nextIsNewLine = true;

            final javassist.bytecode.CodeIterator ci = code.iterator();
            while (ci.hasNext()) {
                final int pc = ci.next();
                if (nextIsNewLine) {
                    entries.add(new int[]{pc, lineInGeneratedClass(statementLines, boundary, methodSignatureLineInClass)});
                    boundary++;
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
            if (statementLines == null || statementLines.isEmpty()
                    || entries.size() != statementLines.size()) {
                // Emit NOTHING rather than a table we already know is wrong. A desync means a
                // codegen change altered the statement shape without the scanner learning about
                // it, so the surviving entries are shifted -- plausible, and wrong. Note also
                // that line_number is an unsigned u2, so an UNRESOLVED (-1) would serialize as
                // 65535 and read as a real line, not as "unknown". Same policy as companion
                // classes: no attribution beats false attribution, and an absent table makes the
                // JVM report an unknown line honestly.
                if (statementLines != null && !statementLines.isEmpty()) {
                    log.warn("MAL LineNumberTable omitted for {}: {} bytecode boundaries vs {} "
                            + "source lines. Frames will report an unknown line.",
                        method.getName(), entries.size(), statementLines.size());
                }
                return;
            }
            for (final int[] entry : entries) {
                if (entry[1] <= 0) {
                    // An unresolved line cannot be represented: u2 would turn -1 into 65535.
                    log.warn("MAL LineNumberTable omitted for {}: an entry had no resolvable "
                        + "line.", method.getName());
                    return;
                }
            }
            final ConstPool cp = mi.getConstPool();
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
                new AttributeInfo(
                    cp, "LineNumberTable", info));
        } catch (Exception e) {
            log.warn("Failed to add LineNumberTable: {}", e.getMessage());
        }
    }

    /**
     * Generated source file line for boundary {@code i}, or {@link DslSourceRef#UNRESOLVED} when the scanner
     * produced no line for it. {@code -1} is deliberate: a bogus-but-plausible line is worse than
     * an obviously absent one, and the JVM tolerates it in a {@code LineNumberTable}.
     */
    private static int lineInGeneratedClass(final java.util.List<Integer> statementLines,
                                            final int index,
                                            final int methodSignatureLineInClass) {
        if (statementLines == null || index >= statementLines.size()) {
            return DslSourceRef.UNRESOLVED;
        }
        final Integer lineInMethod = statementLines.get(index);
        if (lineInMethod == null || lineInMethod <= 0) {
            return DslSourceRef.UNRESOLVED;
        }
        // Method-relative line 1 IS the signature line, so the class-relative line counts from
        // there rather than adding a bare offset.
        return methodSignatureLineInClass + lineInMethod - 1;
    }

    /**
     * Adds {@code LocalVariableTable} for the {@code run(Map)} method,
     * including all generated variables ({@code _metric1}, {@code _metric2}, {@code _t0}, ...).
     */
    void addRunLocalVariableTable(final CtMethod method,
                                   final String className,
                                   final Set<String> varNames) {
        final String sfDesc =
            "L" + MALCodegenHelper.SF.replace('.', '/') + ";";
        final String[][] vars = new String[1 + varNames.size()][];
        vars[0] = new String[]{"samples", "Ljava/util/Map;"};
        int i = 1;
        for (final String name : varNames) {
            vars[i++] = new String[]{name, sfDesc};
        }
        DslGeneratedFileWriter.addLocalVariableTable(method, className, vars);
    }
}
