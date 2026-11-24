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

package org.apache.skywalking.oap.trace.analyzer.v2.compiler;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.skywalking.oap.server.core.dsl.DslJavaSourceText;
import org.apache.skywalking.oap.server.core.dsl.debug.DSLDebugCodegenSwitch;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.debug.TALDebug;

/**
 * Emits the dsl-debugging probes into a generated TAL method. Nothing is emitted unless injection is on when the rule
 * compiles. Each method reads, into {@link #GATE}, whether a session records its run, which the runner that read the
 * rule's gate set on the context, and every probe is guarded by that local: a run no session records reads one plain
 * field.
 */
final class TALDebugCodegen {
    static final String GATE_HOLDER = "org.apache.skywalking.oap.server.core.dsl.debug.GateHolder";
    static final String GATE = "_dbg";
    /**
     * The kinds whose statement holds a body, which a step shows by its first line, as its body's statements have
     * their own steps.
     */
    private static final Set<String> HAS_BODY = new HashSet<>(Arrays.asList("block", "build", "foreach", "rateLimit"));

    private TALDebugCodegen() {
    }

    /**
     * The gate read at the top of a method, with its local variable table entry.
     */
    static void declareGate(final StringBuilder sb, final List<String[]> lvt) {
        if (!DSLDebugCodegenSwitch.isInjectionEnabled()) {
            return;
        }
        sb.append("  boolean ").append(GATE).append(" = ctx.isDebugging();\n");
        lvt.add(new String[] {GATE, "Z"});
    }

    /**
     * A statement that ran.
     *
     * @param value     Java source of what the statement produced, or null when it produces nothing
     * @param valueType the value's type, which picks the probe overload since the generated code has no autoboxing
     */
    static void step(final StringBuilder sb, final TALGenCtx genCtx, final String kind,
                     final TALScriptModel.Located stmt, final String value, final Class<?> valueType) {
        if (!DSLDebugCodegenSwitch.isInjectionEnabled()) {
            return;
        }
        sb.append("  if (").append(GATE).append(") ").append(TALDebug.FQCN).append(".step(this.debug, ctx, ")
          .append(prefix(genCtx, stmt, kind)).append(", ").append(probeValue(value, valueType)).append(");\n");
    }

    /**
     * An {@code if}: the condition was evaluated into {@code resultVar} before the branch reads it.
     */
    static void condition(final StringBuilder sb, final TALGenCtx genCtx, final TALScriptModel.Located stmt,
                          final String resultVar) {
        if (!DSLDebugCodegenSwitch.isInjectionEnabled()) {
            return;
        }
        sb.append("  if (").append(GATE).append(") ").append(TALDebug.FQCN).append(".condition(this.debug, ctx, \"")
          .append(stage(genCtx)).append("\", ").append(stmt.getSourceLine()).append(", \"")
          .append(DslJavaSourceText.toLiteral(stmt.getSourceText())).append("\", ").append(resultVar).append(");\n");
    }

    /**
     * An operand of an {@code if} condition, wrapped so the value it evaluates to is recorded and then used as it
     * was. Short-circuiting is kept: an operand the condition did not evaluate is not recorded.
     *
     * @param expr      Java source of the operand: a reference, or a primitive boolean when {@code primitive}
     * @param text      the operand as the rule writes it
     * @param primitive whether {@code expr} is a primitive boolean, which the boolean overload returns as one
     */
    static String operand(final String expr, final String text, final boolean primitive) {
        return TALDebug.FQCN + ".operand(" + GATE + ", this.debug, ctx, \"" + DslJavaSourceText.toLiteral(text)
            + "\", " + (primitive ? expr : "(Object) " + expr) + ")";
    }

    static void source(final StringBuilder sb, final TALGenCtx genCtx, final TALScriptModel.Located stmt,
                       final String sourceVar) {
        if (!DSLDebugCodegenSwitch.isInjectionEnabled()) {
            return;
        }
        sb.append("  if (").append(GATE).append(") ").append(TALDebug.FQCN).append(".source(this.debug, ctx, \"")
          .append(stage(genCtx)).append("\", ").append(stmt.getSourceLine()).append(", \"")
          .append(DslJavaSourceText.toLiteral(header(stmt.getSourceText()))).append("\", ").append(sourceVar)
          .append(");\n");
    }

    static void metric(final StringBuilder sb, final TALGenCtx genCtx, final TALScriptModel.Located stmt,
                       final String metric, final String entityVar, final String observationVar,
                       final String timeVar) {
        if (!DSLDebugCodegenSwitch.isInjectionEnabled()) {
            return;
        }
        sb.append("      if (").append(GATE).append(") ").append(TALDebug.FQCN).append(".metric(this.debug, ctx, \"")
          .append(stage(genCtx)).append("\", ").append(stmt.getSourceLine()).append(", \"")
          .append(DslJavaSourceText.toLiteral(header(stmt.getSourceText()))).append("\", \"")
          .append(DslJavaSourceText.toLiteral(metric)).append("\", ").append(entityVar).append(", ")
          .append(observationVar).append(", ").append(timeVar).append(");\n");
    }

    /**
     * @param reason Java source of an {@code Object}: an exception, or a string literal
     */
    static void metricSkipped(final StringBuilder sb, final TALGenCtx genCtx, final TALScriptModel.Located stmt,
                              final String metric, final String reason) {
        if (!DSLDebugCodegenSwitch.isInjectionEnabled()) {
            return;
        }
        sb.append("    if (").append(GATE).append(") ").append(TALDebug.FQCN)
          .append(".metricSkipped(this.debug, ctx, \"").append(stage(genCtx)).append("\", ")
          .append(stmt.getSourceLine()).append(", \"")
          .append(DslJavaSourceText.toLiteral(header(stmt.getSourceText()))).append("\", \"")
          .append(DslJavaSourceText.toLiteral(metric)).append("\", ").append(reason).append(");\n");
    }

    /**
     * The block a statement sits in, which the debugger groups the steps of a run by.
     */
    static String stage(final TALGenCtx genCtx) {
        if (genCtx.buildDepth > 0) {
            return "build";
        }
        if (genCtx.firstMethod) {
            return "first";
        }
        if (genCtx.voteDepth > 0) {
            return "persist";
        }
        if (genCtx.extractorDepth > 0) {
            return "extractor";
        }
        return "filter";
    }

    /**
     * A block's first line, such as {@code source Segment {}}, the part of its text a step shows.
     */
    static String header(final String text) {
        if (text == null) {
            return "";
        }
        final int newline = text.indexOf('\n');
        return (newline < 0 ? text : text.substring(0, newline)).trim();
    }

    private static String prefix(final TALGenCtx genCtx, final TALScriptModel.Located stmt, final String kind) {
        final String text = HAS_BODY.contains(kind) ? header(stmt.getSourceText()) : stmt.getSourceText();
        return "\"" + stage(genCtx) + "\", \"" + kind + "\", " + stmt.getSourceLine() + ", \""
            + DslJavaSourceText.toLiteral(text) + "\"";
    }

    private static String probeValue(final String value, final Class<?> valueType) {
        if (value == null) {
            return "(Object) null";
        }
        if (valueType == float.class) {
            return "(double) " + value;
        }
        if (valueType == short.class || valueType == byte.class || valueType == char.class) {
            return "(int) " + value;
        }
        if (valueType != null && valueType.isPrimitive()) {
            return value;
        }
        return "(Object) " + value;
    }
}
