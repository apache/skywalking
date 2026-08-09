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

import lombok.Getter;

/**
 * Where one generated class came from, in both coordinate spaces at once.
 *
 * <p>Compiling a MAL rule produces MORE THAN ONE class file. Javassist cannot emit lambdas or
 * anonymous inner classes, so every closure becomes its own class, and the JVM allows exactly one
 * {@code SourceFile} per class:
 *
 * <pre>
 *   metricsRules:
 *     - name: cpu_total                                   vm.yaml:38   &lt;- ONE operator anchor
 *       exp: node_cpu.tag({...}).forEach({...}).sum([...])
 *            |
 *            +-- vm_L38_cpu_total.java            run()   &lt;- THREE generated files,
 *            +-- vm_L38_cpu_total$_tag.java       apply()     each with its own
 *            +-- vm_L38_cpu_total$_forEach.java   accept()    name and line numbering
 * </pre>
 *
 * <p>That is why the two spaces cannot collapse into a single number: their CARDINALITY differs.
 * One YAML line maps to N generated files, and a file-level {@code filter:} maps the other way —
 * one line, one class, shared by every rule in the document. A lone {@code int line} field cannot
 * express either direction, which is how the {@code LineNumberTable} once ended up holding a
 * statement ordinal that matched no file at all.
 *
 * <p>One instance describes ONE generated file. The class is shared by every kind of generated
 * artifact — main expression class, filter class, closure companion — because they differ only in
 * their values, not in their shape.
 *
 * <ul>
 *   <li><b>{@link #getYamlFile() yamlFile} / {@link #getYamlLine() yamlLine}</b> — the OPERATOR
 *       coordinate, fixed per rule. What the dsl-debugging API reports so a captured sample names
 *       an editable location. Every generated file of one rule repeats the same pair; it is the
 *       join key tying them back together.</li>
 *   <li><b>{@link #getGeneratedClass() generatedClass} / {@link #getGeneratedLine()
 *       generatedLine}</b> — the MACHINE coordinate, unique per generated file. What
 *       {@code SourceFile} and {@code LineNumberTable} carry so a stack frame or an IDE resolves
 *       to real source.</li>
 * </ul>
 *
 * <p><b>Unresolved lines.</b> A line is normally always available — rules are bound from YAML
 * snakeyaml can re-compose, and generated lines are counted in buffers we build ourselves.
 * {@link #UNRESOLVED} is a defensive fallback, deliberately {@code -1} rather than {@code 0}: a
 * silently missing line hides the failure, whereas {@code -1} stays visible and greppable. It is
 * never written into a {@code LineNumberTable}, where the unsigned {@code u2} encoding would turn
 * it into 65535 and make it read as a real line.
 */
@Getter
public final class MalSourceRef {

    /** Marker for "a line was expected here but could not be resolved". */
    public static final int UNRESOLVED = -1;

    private final String yamlFile;
    private final int yamlLine;
    private final String generatedClass;
    private final int generatedLine;

    private MalSourceRef(final String yamlFile,
                         final int yamlLine,
                         final String generatedClass,
                         final int generatedLine) {
        this.yamlFile = yamlFile;
        this.yamlLine = yamlLine;
        this.generatedClass = generatedClass;
        this.generatedLine = generatedLine;
    }

    /**
     * Non-positive lines normalise to {@link #UNRESOLVED}, so a {@code 0} from an older accessor
     * cannot be mistaken for a real line.
     *
     * @param yamlFile       rule file name, e.g. {@code vm.yaml}
     * @param yamlLine       1-based line in that rule file
     * @param generatedClass simple name of the generated class, e.g. {@code vm_L38_cpu_total$_tag}
     * @param generatedLine  1-based line within that class's {@code .java}
     * @return the coordinates of one generated file
     */
    public static MalSourceRef of(final String yamlFile,
                                  final int yamlLine,
                                  final String generatedClass,
                                  final int generatedLine) {
        return new MalSourceRef(
            yamlFile,
            yamlLine > 0 ? yamlLine : UNRESOLVED,
            generatedClass,
            generatedLine > 0 ? generatedLine : UNRESOLVED);
    }

    /**
     * The rule anchor alone, before any class has been generated from it.
     *
     * <p>Two-phase by necessity, not by taste: a generated class name embeds the rule's line
     * ({@code vm_L38_cpu_total}), so the anchor must exist BEFORE the class it will later name.
     * {@link #inGeneratedClass} completes it once that name is known.
     *
     * @param yamlFile rule file name, e.g. {@code vm.yaml}
     * @param yamlLine 1-based line in that rule file
     * @return the operator coordinate, with no generated file yet
     */
    public static MalSourceRef ofRule(final String yamlFile, final int yamlLine) {
        return of(yamlFile, yamlLine, null, UNRESOLVED);
    }

    /**
     * Completes this anchor for one generated file. The rule half is carried over unchanged, which
     * is what makes every artifact of a rule agree on its operator coordinate.
     *
     * @param generatedClass simple name of the generated class
     * @param generatedLine  1-based line within that class's {@code .java}
     * @return a ref describing that one generated file
     */
    public MalSourceRef inGeneratedClass(final String generatedClass, final int generatedLine) {
        return of(yamlFile, yamlLine, generatedClass, generatedLine);
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
    public static MalSourceRef parse(final String fused) {
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
     * The {@code .java} this class's {@code SourceFile} attribute names. Deriving it here rather
     * than at each call site is what keeps the attribute and the file written to disk in step —
     * they disagreed for every closure companion until the sidecar was written.
     *
     * @return the source file name, or {@code null} when no class name is known
     */
    public String generatedFileName() {
        return generatedClass == null ? null : generatedClass + ".java";
    }

    /**
     * @return the operator-facing location, e.g. {@code vm.yaml:38}
     */
    public String describeYaml() {
        return yamlFile + ":" + yamlLine;
    }

    /**
     * @return the machine-facing location, e.g. {@code vm_L38_cpu_total$_tag.java:9}
     */
    public String describeGenerated() {
        return generatedFileName() + ":" + generatedLine;
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
        return "MalSourceRef(" + describeYaml() + " -> " + describeGenerated() + ")";
    }
}
