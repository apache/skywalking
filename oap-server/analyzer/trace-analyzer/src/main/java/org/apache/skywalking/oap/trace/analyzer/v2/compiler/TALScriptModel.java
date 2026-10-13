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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.BinaryOp;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.CompareOp;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.LogicalOp;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALBoolConditionValue;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALComparisonCondition;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALCondition;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALConditionValue;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALDefStatement;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALExprCondition;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALFieldSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALFunctionArg;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALFunctionCallConditionValue;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALIndexSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALLogicalCondition;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALMethodSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALNotCondition;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALNullConditionValue;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALNumberConditionValue;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALStringConditionValue;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALValueAccess;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALValueAccessConditionValue;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALValueAccessSegment;

/**
 * The immutable parse tree of one TAL rule, produced by {@link TALScriptParser} and read by
 * {@link TALClassGenerator}.
 *
 * <p>The expression types implement the {@code model} interfaces, which is the view
 * {@link TALValueCodegen} compiles against; the statement types are TAL's own and have no counterpart there.
 */
@Getter
public final class TALScriptModel {
    private final List<FilterStatement> statements;

    public TALScriptModel(final List<FilterStatement> statements) {
        this.statements = Collections.unmodifiableList(statements);
    }

    // ==================== Statements ====================

    /**
     * Anything that can appear directly inside {@code filter { }}.
     */
    public interface FilterStatement {
    }

    /**
     * Anything that can appear inside {@code extractor { }}.
     */
    public interface ExtractorStatement {
    }

    /**
     * Anything that can appear inside {@code sink { }} or {@code persist { }}. The two blocks take the same
     * statements and differ only in what they decide: the record, or the span.
     */
    public interface VoteStatement {
    }

    /**
     * {@code abort {}} — stop the rule for this span. Legal at filter level and inside every block.
     */
    public static final class AbortStatement
        implements FilterStatement, ExtractorStatement, VoteStatement {
    }

    @Getter
    public static final class DefStatement
        implements FilterStatement, ExtractorStatement, TALDefStatement {
        private final String varName;
        private final ValueAccess initializer;
        private final String castType;
        private int sourceLine;
        private String sourceText = "";

        public DefStatement(final String varName, final ValueAccess initializer, final String castType) {
            this.varName = varName;
            this.initializer = initializer;
            this.castType = castType;
        }

        public DefStatement withSource(final int line, final String text) {
            this.sourceLine = line;
            this.sourceText = text == null ? "" : text;
            return this;
        }
    }

    @Getter
    public static final class ExtractorBlock implements FilterStatement {
        private final List<ExtractorStatement> statements;

        public ExtractorBlock(final List<ExtractorStatement> statements) {
            this.statements = Collections.unmodifiableList(statements);
        }
    }

    /**
     * The standard identity fields an extractor may overwrite on {@code meta}, which are also the record's
     * standard fields when the rule declares an {@code outputType}.
     */
    public enum FieldType {
        SERVICE, INSTANCE, ENDPOINT, LAYER
    }

    @Getter
    public static final class FieldAssignment implements ExtractorStatement, FilterStatement {
        private final FieldType fieldType;
        private final ValueAccess value;
        private final String castType;
        private int sourceLine;
        private String sourceText = "";

        public FieldAssignment(final FieldType fieldType, final ValueAccess value, final String castType) {
            this.fieldType = fieldType;
            this.value = value;
            this.castType = castType;
        }

        public FieldAssignment withSource(final int line, final String text) {
            this.sourceLine = line;
            this.sourceText = text == null ? "" : text;
            return this;
        }
    }

    /**
     * A statement whose name is not a keyword: it sets a field on the rule's {@code outputType}, validated
     * against a setter at compile time.
     */
    @Getter
    public static final class OutputFieldAssignment implements ExtractorStatement, FilterStatement {
        private final String fieldName;
        private final ValueAccess value;
        private final String castType;
        private int sourceLine;
        private String sourceText = "";

        public OutputFieldAssignment(final String fieldName, final ValueAccess value, final String castType) {
            this.fieldName = fieldName;
            this.value = value;
            this.castType = castType;
        }

        public OutputFieldAssignment withSource(final int line, final String text) {
            this.sourceLine = line;
            this.sourceText = text == null ? "" : text;
            return this;
        }
    }

    /**
     * {@code alias <address>} — register the address as an alias of the span's service; the runtime helper's
     * {@code alias} says what that means.
     */
    @Getter
    public static final class AliasStatement implements ExtractorStatement, FilterStatement {
        private final ValueAccess address;
        private final String castType;
        private int sourceLine;
        private String sourceText = "";

        public AliasStatement(final ValueAccess address, final String castType) {
            this.address = address;
            this.castType = castType;
        }

        public AliasStatement withSource(final int line, final String text) {
            this.sourceLine = line;
            this.sourceText = text == null ? "" : text;
            return this;
        }
    }

    /**
     * {@code tag 'key': value} — appended to the persisted span as a string attribute.
     */
    @Getter
    public static final class TagAssignment implements ExtractorStatement, FilterStatement {
        private final Map<String, TagValue> tags;
        private int sourceLine;
        private String sourceText = "";

        public TagAssignment(final Map<String, TagValue> tags) {
            this.tags = Collections.unmodifiableMap(tags);
        }

        public TagAssignment withSource(final int line, final String text) {
            this.sourceLine = line;
            this.sourceText = text == null ? "" : text;
            return this;
        }
    }

    @Getter
    public static final class TagValue {
        private final ValueAccess value;
        private final String castType;

        public TagValue(final ValueAccess value, final String castType) {
            this.value = value;
            this.castType = castType;
        }
    }

    /**
     * One {@code metrics { }} block: a metric name, the entity it lands on, the function that aggregates it and
     * the observation. {@code name} and {@code function} are literals and {@code scope}'s constructor is known at
     * compile time, so the compiler knows every metric a rule declares before a span arrives.
     */
    @Getter
    public static final class MetricsBlock implements ExtractorStatement, FilterStatement {
        private final String name;
        private final ValueAccess scope;
        private final String function;
        private final ValueAccess value;
        /**
         * The {@code as} cast written after {@code value}, or null.
         */
        private final String valueCast;
        /**
         * Required by {@code percent}, forbidden elsewhere: the span counts as a match when this holds.
         */
        private final Condition match;
        /**
         * Required by {@code apdex}, forbidden elsewhere: the span counts as successful when this holds.
         */
        private final Condition status;
        private final Map<String, TagValue> labels;
        /**
         * The {@code labelLimit} as written, or null for the default.
         */
        private final String labelLimit;
        /**
         * Ascending bucket bounds, in the unit of {@code value}; required by {@code percentile}. Kept as the literal
         * text so the compiler preserves the declared numeric type.
         */
        private final List<String> buckets;
        private final List<String> ranks;
        private final ValueAccess timestamp;
        /**
         * The {@code as} cast written after {@code timestamp}, or null.
         */
        private final String timestampCast;
        private int sourceLine;
        private String sourceText = "";

        public MetricsBlock(final String name,
                            final ValueAccess scope,
                            final String function,
                            final ValueAccess value,
                            final String valueCast,
                            final Condition match,
                            final Condition status,
                            final Map<String, TagValue> labels,
                            final String labelLimit,
                            final List<String> buckets,
                            final List<String> ranks,
                            final ValueAccess timestamp,
                            final String timestampCast) {
            this.name = name;
            this.scope = scope;
            this.function = function;
            this.value = value;
            this.valueCast = valueCast;
            this.match = match;
            this.status = status;
            this.labels = labels == null ? Collections.emptyMap() : Collections.unmodifiableMap(labels);
            this.labelLimit = labelLimit;
            this.buckets = buckets == null ? Collections.emptyList() : Collections.unmodifiableList(buckets);
            this.ranks = ranks == null ? Collections.emptyList() : Collections.unmodifiableList(ranks);
            this.timestamp = timestamp;
            this.timestampCast = timestampCast;
        }

        public MetricsBlock withSource(final int line, final String text) {
            this.sourceLine = line;
            this.sourceText = text == null ? "" : text;
            return this;
        }
    }

    /**
     * {@code sink { }} — what happens to the record the rule built. A rule without an {@code outputType} has no
     * record, so a sink in it is a compile error rather than a silent no-op.
     */
    @Getter
    public static final class SinkBlock implements FilterStatement {
        private final List<VoteStatement> statements;

        public SinkBlock(final List<VoteStatement> statements) {
            this.statements = Collections.unmodifiableList(statements);
        }
    }

    /**
     * {@code persist { }} — the span's own fate. A rule without this block abstains.
     */
    @Getter
    public static final class PersistBlock implements FilterStatement {
        private final List<VoteStatement> statements;

        public PersistBlock(final List<VoteStatement> statements) {
            this.statements = Collections.unmodifiableList(statements);
        }
    }

    @Getter
    public static final class SamplerBlock implements VoteStatement, FilterStatement {
        private final List<SamplerContent> contents;

        public SamplerBlock(final List<SamplerContent> contents) {
            this.contents = Collections.unmodifiableList(contents);
        }
    }

    public interface SamplerContent {
    }

    @Getter
    public static final class RateLimitBlock implements SamplerContent {
        private final String id;
        private final List<InterpolationPart> idParts;
        private final int rpm;

        public RateLimitBlock(final String id, final List<InterpolationPart> idParts, final int rpm) {
            this.id = id;
            this.idParts = idParts == null ? Collections.emptyList() : Collections.unmodifiableList(idParts);
            this.rpm = rpm;
        }

        /**
         * @return whether the id contains {@code ${...}} placeholders and has to be built per span
         */
        public boolean isIdInterpolated() {
            return !idParts.isEmpty();
        }
    }

    @Getter
    public static final class InterpolationPart {
        private final String text;
        private final ValueAccess expression;

        private InterpolationPart(final String text, final ValueAccess expression) {
            this.text = text;
            this.expression = expression;
        }

        public static InterpolationPart ofLiteral(final String text) {
            return new InterpolationPart(text, null);
        }

        public static InterpolationPart ofExpression(final ValueAccess expr) {
            return new InterpolationPart(null, expr);
        }

        public boolean isLiteral() {
            return expression == null;
        }
    }

    /**
     * Vote keep regardless of the samplers in the same rule, and, in a {@code persist} block, ask the forward to
     * skip the receiver's trace-id head sampling for this span.
     */
    public static final class EnforcerStatement implements VoteStatement, FilterStatement {
    }

    public static final class DropperStatement implements VoteStatement, FilterStatement {
    }

    /**
     * {@code if / else if / else}. An {@code else if} chain is nested: the else branch of one block holds the
     * next, so the generator emits one shape rather than two.
     */
    @Getter
    public static final class IfBlock
        implements FilterStatement, ExtractorStatement, VoteStatement, SamplerContent {
        private final Condition condition;
        private final List<FilterStatement> thenStatements;
        private final List<FilterStatement> elseStatements;

        public IfBlock(final Condition condition,
                       final List<FilterStatement> thenStatements,
                       final List<FilterStatement> elseStatements) {
            this.condition = condition;
            this.thenStatements = Collections.unmodifiableList(thenStatements);
            this.elseStatements = elseStatements == null
                ? Collections.emptyList() : Collections.unmodifiableList(elseStatements);
        }
    }

    // ==================== Conditions ====================

    public interface Condition extends TALCondition {
    }

    @Getter
    public static final class ComparisonCondition implements Condition, TALComparisonCondition {
        private final ValueAccess left;
        private final String leftCast;
        private final CompareOp op;
        private final ConditionValue right;

        public ComparisonCondition(final ValueAccess left,
                                   final String leftCast,
                                   final CompareOp op,
                                   final ConditionValue right) {
            this.left = left;
            this.leftCast = leftCast;
            this.op = op;
            this.right = right;
        }
    }

    @Getter
    public static final class LogicalCondition implements Condition, TALLogicalCondition {
        private final Condition left;
        private final LogicalOp op;
        private final Condition right;

        public LogicalCondition(final Condition left, final LogicalOp op, final Condition right) {
            this.left = left;
            this.op = op;
            this.right = right;
        }
    }

    @Getter
    public static final class NotCondition implements Condition, TALNotCondition {
        private final Condition inner;

        public NotCondition(final Condition inner) {
            this.inner = inner;
        }
    }

    @Getter
    public static final class ExprCondition implements Condition, TALExprCondition {
        private final ValueAccess expr;
        private final String castType;

        public ExprCondition(final ValueAccess expr, final String castType) {
            this.expr = expr;
            this.castType = castType;
        }
    }

    // ==================== Condition values ====================

    public interface ConditionValue extends TALConditionValue {
    }

    @Getter
    public static final class StringConditionValue implements ConditionValue, TALStringConditionValue {
        private final String value;

        public StringConditionValue(final String value) {
            this.value = value;
        }
    }

    @Getter
    public static final class NumberConditionValue implements ConditionValue, TALNumberConditionValue {
        private final double value;
        /**
         * The literal exactly as the rule wrote it, which is what preserves the declared numeric type.
         */
        private final String literal;

        public NumberConditionValue(final double value) {
            this(value, null);
        }

        public NumberConditionValue(final double value, final String literal) {
            this.value = value;
            this.literal = literal;
        }
    }

    @Getter
    public static final class BoolConditionValue implements ConditionValue, TALBoolConditionValue {
        private final boolean value;

        public BoolConditionValue(final boolean value) {
            this.value = value;
        }
    }

    public static final class NullConditionValue implements ConditionValue, TALNullConditionValue {
    }

    @Getter
    public static final class ValueAccessConditionValue implements ConditionValue, TALValueAccessConditionValue {
        private final ValueAccess value;
        private final String castType;

        public ValueAccessConditionValue(final ValueAccess value, final String castType) {
            this.value = value;
            this.castType = castType;
        }
    }

    @Getter
    public static final class FunctionCallConditionValue implements ConditionValue, TALFunctionCallConditionValue {
        private final String functionName;
        private final List<String> arguments;

        public FunctionCallConditionValue(final String functionName, final List<String> arguments) {
            this.functionName = functionName;
            this.arguments = Collections.unmodifiableList(arguments);
        }
    }

    // ==================== Value access ====================

    @Getter
    public static final class ValueAccess implements TALValueAccess {
        private final List<String> segments;
        private final boolean spanRef;
        private final boolean metaRef;
        private final boolean stringLiteral;
        private final boolean numberLiteral;
        private final List<ValueAccessSegment> chain;
        private final String functionCallName;
        private final List<FunctionArg> functionCallArgs;
        private final List<ValueAccess> concatParts;
        private final List<BinaryOp> concatOps;
        private final ValueAccess parenInner;
        private final String parenCast;

        public ValueAccess(final List<String> segments,
                           final boolean spanRef,
                           final boolean metaRef,
                           final List<ValueAccessSegment> chain) {
            this(segments, spanRef, metaRef, false, false, chain, null,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null, null);
        }

        public ValueAccess(final List<String> segments,
                           final boolean spanRef,
                           final boolean metaRef,
                           final boolean stringLiteral,
                           final boolean numberLiteral,
                           final List<ValueAccessSegment> chain,
                           final String functionCallName,
                           final List<FunctionArg> functionCallArgs) {
            this(segments, spanRef, metaRef, stringLiteral, numberLiteral, chain,
                functionCallName, functionCallArgs,
                Collections.emptyList(), Collections.emptyList(), null, null);
        }

        public ValueAccess(final List<String> segments,
                           final boolean spanRef,
                           final boolean metaRef,
                           final boolean stringLiteral,
                           final boolean numberLiteral,
                           final List<ValueAccessSegment> chain,
                           final String functionCallName,
                           final List<FunctionArg> functionCallArgs,
                           final List<ValueAccess> concatParts,
                           final List<BinaryOp> concatOps,
                           final ValueAccess parenInner,
                           final String parenCast) {
            this.segments = Collections.unmodifiableList(segments);
            this.spanRef = spanRef;
            this.metaRef = metaRef;
            this.stringLiteral = stringLiteral;
            this.numberLiteral = numberLiteral;
            this.chain = chain != null ? Collections.unmodifiableList(chain) : Collections.emptyList();
            this.functionCallName = functionCallName;
            this.functionCallArgs = functionCallArgs != null
                ? Collections.unmodifiableList(functionCallArgs) : Collections.emptyList();
            this.concatParts = concatParts != null
                ? Collections.unmodifiableList(concatParts) : Collections.emptyList();
            this.concatOps = concatOps != null
                ? Collections.unmodifiableList(concatOps) : Collections.emptyList();
            this.parenInner = parenInner;
            this.parenCast = parenCast;
        }

        @Override
        public String toPathString() {
            return String.join(".", segments);
        }

        /**
         * TAL's input root is {@code span}, the wire object the rule declared as its input type.
         */
        @Override
        public boolean isInputRef() {
            return spanRef;
        }

        /**
         * TAL's metadata root is {@code meta}.
         */
        @Override
        public boolean isMetaRef() {
            return metaRef;
        }

        /**
         * TAL has no static helper root; its entity constructors are ordinary function calls.
         */
        @Override
        public boolean isStaticRef() {
            return false;
        }
    }

    @Getter
    public static final class FunctionArg implements TALFunctionArg {
        private final ValueAccess value;
        private final String castType;

        public FunctionArg(final ValueAccess value, final String castType) {
            this.value = value;
            this.castType = castType;
        }
    }

    public interface ValueAccessSegment extends TALValueAccessSegment {
    }

    @Getter
    public static final class FieldSegment implements ValueAccessSegment, TALFieldSegment {
        private final String name;
        private final boolean safeNav;

        public FieldSegment(final String name, final boolean safeNav) {
            this.name = name;
            this.safeNav = safeNav;
        }
    }

    @Getter
    public static final class MethodSegment implements ValueAccessSegment, TALMethodSegment {
        private final String name;
        private final List<FunctionArg> arguments;
        private final boolean safeNav;

        public MethodSegment(final String name, final List<FunctionArg> arguments, final boolean safeNav) {
            this.name = name;
            this.arguments = arguments != null
                ? Collections.unmodifiableList(arguments) : Collections.emptyList();
            this.safeNav = safeNav;
        }
    }

    @Getter
    public static final class IndexSegment implements ValueAccessSegment, TALIndexSegment {
        private final int index;

        public IndexSegment(final int index) {
            this.index = index;
        }
    }
}
