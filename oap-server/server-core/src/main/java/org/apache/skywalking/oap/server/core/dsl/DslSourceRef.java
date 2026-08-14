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

import lombok.Getter;

/**
 * The rule coordinate a generated class came from: a file, and a line inside it.
 *
 * <p>A value object. It DESCRIBES a location and renders it; writing files and stamping bytecode
 * live in {@link DslGeneratedFileWriter}, because knowing a coordinate and performing I/O with it
 * are separate concerns.
 *
 * <p>Shared by OAL, MAL, LAL and Hierarchy. Their grammars differ but the compile workflow does
 * not: a rule at {@code file:line} becomes a class, and a frame from that class has to lead back
 * to the rule. Each generator used to implement this itself and each drifted differently, which
 * is why Hierarchy named the rule instead of the class, MAL companions fell through to Javassist's
 * default, and LAL never resolved a line at all.
 *
 * <p><b>This holds the rule coordinate only — deliberately not the generated one.</b> Compiling
 * one MAL rule produces MORE THAN ONE class file: Javassist cannot emit lambdas or anonymous inner
 * classes, so every closure becomes its own class, and the JVM allows exactly one
 * {@code SourceFile} per class:
 *
 * <pre>
 *   metricsRules:
 *     - name: cpu_total                                 otel-rules/vm.yaml:38  &lt;- ONE rule anchor
 *       exp: node_cpu.tag({...}).forEach({...}).sum([...])
 *            |
 *            +-- otel_rules_vm_L38_cpu_total.java          run()     &lt;- THREE generated files,
 *            +-- otel_rules_vm_L38_cpu_total$_tag.java     apply()      each with its own
 *            +-- otel_rules_vm_L38_cpu_total$_forEach.java accept()     name and line numbering
 * </pre>
 *
 * <p>The cardinalities differ: one rule line maps to N generated files, and a file-level
 * {@code filter:} maps the other way — one line, one class, shared by every rule in the document.
 * A generated line therefore cannot be a field here; it belongs to a specific method in a specific
 * generated file, and is resolved at the point of use by
 * {@link DslGeneratedFileWriter#lineOfMethod}. Putting it here is what produced the
 * {@code LineNumberTable} full of statement ordinals matching no file at all.
 *
 * <p>So one instance describes one RULE, and every generated file of that rule carries the same
 * pair — it is the join key tying them back together, and what the dsl-debugging API reports so a
 * captured sample names an editable location.
 *
 * <p><b>One entrance, on purpose — do not add a producer interface.</b> Each DSL converts its own
 * config into this type at its own call site: MAL from {@code MetricRuleConfig}, LAL from
 * {@code LALConfig}'s fields, OAL from a {@code SourceLocation}, Hierarchy from a constant plus a
 * name-to-line map. That looks like four answers to one question, and a shared
 * {@code sourcePath()/lineNo()} interface has been proposed to unify it. It should not be added:
 * two of the four cannot implement it. OAL's dispatcher deliberately wants a LINELESS ref, because
 * one dispatcher spans every metric of a scope, and Hierarchy has no per-rule object to hang an
 * interface on. The remaining two would gain little, and the escape hatches for the other two
 * would cost more than the duplication. The narrow entrance below — a file and a line — is the
 * contract; what each DSL knows about its own rules stays where that knowledge lives.
 *
 * <p><b>Unresolved lines.</b> A line is normally always available: rules are bound from YAML
 * snakeyaml can re-compose. {@link #UNRESOLVED} is a defensive fallback, deliberately {@code -1}
 * rather than {@code 0}: a silently missing line hides the failure, whereas {@code -1} stays
 * visible and greppable. It is never written into a {@code LineNumberTable}, where the unsigned
 * {@code u2} encoding would turn it into 65535 and make it read as a real line.
 */
@Getter
public final class DslSourceRef {

    /** Marker for "a line was expected here but could not be resolved". */
    public static final int UNRESOLVED = -1;

    private final String yamlFile;
    private final int yamlLine;

    private DslSourceRef(final String yamlFile, final int yamlLine) {
        this.yamlFile = yamlFile;
        this.yamlLine = yamlLine;
    }

    /**
     * The rule anchor alone, before any class has been generated from it.
     *
     * <p>Non-positive lines normalise to {@link #UNRESOLVED}, so a {@code 0} cannot be mistaken
     * for a real line.
     *
     * @param yamlFile rule file name, e.g. {@code vm.yaml}
     * @param yamlLine 1-based line in that rule file
     * @return the operator coordinate, with no generated file yet
     */
    public static DslSourceRef ofRule(final String yamlFile, final int yamlLine) {
        return new DslSourceRef(yamlFile, yamlLine > 0 ? yamlLine : UNRESOLVED);
    }

    /**
     * Parses the {@code "vm.yaml:38"} wire form.
     *
     * <p>This is the ONLY parser of that form, as {@link #describeYaml()} is its only renderer.
     * The format survives because it crosses module boundaries as a single string; keeping both
     * ends here is what stops the file and the line drifting apart, which is exactly what happened
     * when two call sites each re-split it with their own {@code lastIndexOf(':')}.
     *
     * @param fused the rendered form, or {@code null}
     * @return the rule anchor; an unparseable line yields {@link #UNRESOLVED} rather than throwing
     */
    public static DslSourceRef parse(final String fused) {
        if (fused == null) {
            return ofRule(null, UNRESOLVED);
        }
        final int colonIdx = fused.lastIndexOf(':');
        if (colonIdx <= 0) {
            return ofRule(fused, UNRESOLVED);
        }
        int line;
        try {
            line = Integer.parseInt(fused.substring(colonIdx + 1).trim());
        } catch (final NumberFormatException e) {
            line = UNRESOLVED;
        }
        return ofRule(fused.substring(0, colonIdx), line);
    }

    /**
     * @return the operator-facing location, e.g. {@code vm.yaml:38}
     */
    public String describeYaml() {
        return yamlFile + ":" + yamlLine;
    }

    /**
     * Render a line for use inside a generated Java identifier. {@code -1} is not legal in an
     * identifier, so an unresolved line becomes the literal {@code unknown} — still visible and
     * greppable in a class name, but syntactically valid.
     *
     * @param line a line in either coordinate space
     * @return the identifier-safe rendering
     */
    public static String toIdentifierSegment(final int line) {
        return line > 0 ? Integer.toString(line) : "unknown";
    }

    @Override
    public String toString() {
        return "DslSourceRef(" + describeYaml() + ")";
    }

    // ==================== Compile-time behaviour, shared by all four DSLs ====================

    /**
     * The {@code _L38_} segment of a generated class name.
     *
     * @return the segment including both underscores
     */
    public String classNameSegment() {
        return "_L" + toIdentifierSegment(yamlLine) + "_";
    }

    /**
     * The {@code SourceFile} attribute value: the rule location, then the generated file name.
     *
     * <p>It names the RULE rather than the generated source file because the generated source file is written only when class
     * dumping is enabled — Javassist compiles from an in-memory string, so a default deployment
     * has no {@code .java} on disk and naming it would name nothing. The class name cannot
     * substitute either: sanitising maps {@code /}, {@code -} and {@code .} all to {@code _}.
     *
     * @param generatedSimpleName simple name of the generated class
     * @return {@code (rule/path.yaml:38)Generated.java}, or the bare file name when no path is known
     */
    public String sourceFileOf(final String generatedSimpleName) {
        final String generated = generatedSimpleName + ".java";
        if (yamlFile == null) {
            return generated;
        }
        return "(" + yamlFile + (yamlLine > 0 ? ":" + yamlLine : "") + ")" + generated;
    }
}
