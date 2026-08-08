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
 * The two source coordinates of one generated statement, carried together.
 *
 * <p>A compiled MAL rule exists in two independent coordinate spaces, and they must never be
 * substituted for one another:
 *
 * <ul>
 *   <li><b>{@link #getYamlLine() yamlLine}</b> — the OPERATOR coordinate: a line in the rule
 *       {@code .yaml} the operator wrote. Consumed by the dsl-debugging API so a captured sample
 *       can say which line of which rule file produced it. A single generated method mixes
 *       several of these, because {@code expPrefix} / {@code exp} / {@code expSuffix} are
 *       separate locations spliced into one expression.</li>
 *   <li><b>{@link #getGenLine() genLine}</b> — the MACHINE coordinate: a 1-based line within the
 *       generated METHOD BODY, counted as the codegen appends to its buffer. Consumed by the
 *       bytecode {@code LineNumberTable} so an IDE or {@code javap} can resolve a stack frame
 *       back to the generated class's {@code .java} source. It is relative to the METHOD, not the
 *       class file — the class wrapper above the method body is added later, so
 *       {@code MALBytecodeHelper.addLineNumberTable} converts it using the method signature's
 *       line in the class. Converting in exactly one place is what keeps this field meaning one
 *       thing.</li>
 * </ul>
 *
 * <p>They are paired here deliberately. Before this type existed, one overloaded {@code yamlSource}
 * string served as class label, file identifier and line coordinate at once, which is precisely
 * how the two spaces got conflated: the {@code LineNumberTable} ended up holding neither a YAML
 * line nor a line in the generated class, but a statement ordinal that matched no file at all. Keeping both
 * numbers in one value with distinct accessors makes a mix-up a visible mistake rather than a
 * silent default.
 *
 * <p><b>Unresolved lines.</b> In practice a line is ALWAYS available — every rule is bound from a
 * YAML document snakeyaml can re-compose, and every statement is emitted into a source buffer we
 * build ourselves. {@link #UNRESOLVED} therefore marks a defensive fallback, not a normal state,
 * and is deliberately {@code -1} rather than {@code 0} or an omitted value: a silently missing
 * line hides the failure, whereas {@code -1} propagates visibly all the way into the class name
 * and the {@code SourceFile} attribute, where it can be spotted and grepped for. If you see
 * {@code -1} in a generated artifact, line resolution broke — that is a bug to chase, not a
 * supported mode.
 */
@Getter
public final class MalSourceRef {

    /** Marker for "a line was expected here but could not be resolved". */
    public static final int UNRESOLVED = -1;

    /** Neither coordinate resolved. */
    public static final MalSourceRef UNKNOWN = new MalSourceRef(UNRESOLVED, UNRESOLVED);

    private final int yamlLine;
    private final int genLine;

    private MalSourceRef(final int yamlLine, final int genLine) {
        this.yamlLine = yamlLine;
        this.genLine = genLine;
    }

    /**
     * Non-positive inputs normalise to {@link #UNRESOLVED}, so callers may pass through a
     * {@code 0} from an older accessor without it being mistaken for a real line.
     *
     * @param yamlLine 1-based line in the source rule YAML
     * @param genLine  1-based line within the generated method body
     * @return the paired coordinates
     */
    public static MalSourceRef of(final int yamlLine, final int genLine) {
        final int yaml = yamlLine > 0 ? yamlLine : UNRESOLVED;
        final int gen = genLine > 0 ? genLine : UNRESOLVED;
        if (yaml == UNRESOLVED && gen == UNRESOLVED) {
            return UNKNOWN;
        }
        return new MalSourceRef(yaml, gen);
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
        return "MalSourceRef(yaml=" + yamlLine + ", gen=" + genLine + ")";
    }
}
