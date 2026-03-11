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

package org.apache.skywalking.oap.log.analyzer.v2.compiler;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/**
 * Immutable AST model for LAL (Log Analysis Language) scripts.
 *
 * <p>Represents parsed scripts like:
 * <pre>
 *   filter {
 *     json {}
 *     extractor { service parsed.service as String }
 *     sink { sampler { rateLimit("id") { rpm 6000 } } }
 *   }
 * </pre>
 */
public final class LALScriptModel {

    @Getter
    private final List<FilterStatement> statements;

    public LALScriptModel(final List<FilterStatement> statements) {
        this.statements = Collections.unmodifiableList(statements);
    }

    // ==================== Filter statements ====================

    public interface FilterStatement {
    }

    // ==================== Parser blocks ====================

    @Getter
    public static final class TextParser implements FilterStatement {
        private final String regexpPattern;
        private final boolean abortOnFailure;

        public TextParser(final String regexpPattern, final boolean abortOnFailure) {
            this.regexpPattern = regexpPattern;
            this.abortOnFailure = abortOnFailure;
        }
    }

    @Getter
    public static final class JsonParser implements FilterStatement {
        private final boolean abortOnFailure;

        public JsonParser(final boolean abortOnFailure) {
            this.abortOnFailure = abortOnFailure;
        }
    }

    @Getter
    public static final class YamlParser implements FilterStatement {
        private final boolean abortOnFailure;

        public YamlParser(final boolean abortOnFailure) {
            this.abortOnFailure = abortOnFailure;
        }
    }

    public static final class AbortStatement implements FilterStatement {
    }

    // ==================== Local variable declaration ====================

    @Getter
    public static final class DefStatement implements FilterStatement, ExtractorStatement {
        private final String varName;
        private final ValueAccess initializer;
        private final String castType;

        public DefStatement(final String varName,
                            final ValueAccess initializer,
                            final String castType) {
            this.varName = varName;
            this.initializer = initializer;
            this.castType = castType;
        }
    }

    // ==================== Extractor block ====================

    @Getter
    public static final class ExtractorBlock implements FilterStatement {
        private final List<ExtractorStatement> statements;

        public ExtractorBlock(final List<ExtractorStatement> statements) {
            this.statements = Collections.unmodifiableList(statements);
        }
    }

    public interface ExtractorStatement {
    }

    @Getter
    public static final class FieldAssignment implements ExtractorStatement, FilterStatement {
        private final FieldType fieldType;
        private final ValueAccess value;
        private final String castType;
        private final String formatPattern;

        public FieldAssignment(final FieldType fieldType,
                               final ValueAccess value,
                               final String castType,
                               final String formatPattern) {
            this.fieldType = fieldType;
            this.value = value;
            this.castType = castType;
            this.formatPattern = formatPattern;
        }
    }

    @Getter
    public static final class OutputFieldAssignment implements ExtractorStatement, FilterStatement {
        private final String fieldName;
        private final ValueAccess value;
        private final String castType;

        public OutputFieldAssignment(final String fieldName,
                                     final ValueAccess value,
                                     final String castType) {
            this.fieldName = fieldName;
            this.value = value;
            this.castType = castType;
        }
    }

    public enum FieldType {
        SERVICE, INSTANCE, ENDPOINT, LAYER,
        TRACE_ID, SEGMENT_ID, SPAN_ID, TIMESTAMP
    }

    @Getter
    public static final class TagAssignment implements ExtractorStatement, FilterStatement {
        private final Map<String, TagValue> tags;

        public TagAssignment(final Map<String, TagValue> tags) {
            this.tags = Collections.unmodifiableMap(tags);
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

    @Getter
    public static final class MetricsBlock implements ExtractorStatement, FilterStatement {
        private final String name;
        private final ValueAccess timestampValue;
        private final String timestampCast;
        private final Map<String, TagValue> labels;
        private final ValueAccess value;
        private final String valueCast;

        public MetricsBlock(final String name,
                            final ValueAccess timestampValue,
                            final String timestampCast,
                            final Map<String, TagValue> labels,
                            final ValueAccess value,
                            final String valueCast) {
            this.name = name;
            this.timestampValue = timestampValue;
            this.timestampCast = timestampCast;
            this.labels = labels != null ? Collections.unmodifiableMap(labels) : Collections.emptyMap();
            this.value = value;
            this.valueCast = valueCast;
        }
    }

    // ==================== Sink block ====================

    @Getter
    public static final class SinkBlock implements FilterStatement {
        private final List<SinkStatement> statements;

        public SinkBlock(final List<SinkStatement> statements) {
            this.statements = Collections.unmodifiableList(statements);
        }
    }

    public interface SinkStatement {
    }

    @Getter
    public static final class SamplerBlock implements SinkStatement, FilterStatement {
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
        private final long rpm;

        public RateLimitBlock(final String id,
                              final List<InterpolationPart> idParts,
                              final long rpm) {
            this.id = id;
            this.idParts = idParts != null
                ? Collections.unmodifiableList(idParts) : Collections.emptyList();
            this.rpm = rpm;
        }

        public boolean isIdInterpolated() {
            return !idParts.isEmpty();
        }
    }

    @Getter
    public static final class InterpolationPart {
        private final String literal;
        private final ValueAccess expression;

        private InterpolationPart(final String literal, final ValueAccess expression) {
            this.literal = literal;
            this.expression = expression;
        }

        public static InterpolationPart ofLiteral(final String text) {
            return new InterpolationPart(text, null);
        }

        public static InterpolationPart ofExpression(final ValueAccess expr) {
            return new InterpolationPart(null, expr);
        }

        public boolean isLiteral() {
            return literal != null;
        }
    }

    public static final class EnforcerStatement implements SinkStatement, FilterStatement {
    }

    public static final class DropperStatement implements SinkStatement, FilterStatement {
    }

    // ==================== Control flow ====================

    @Getter
    public static final class IfBlock implements FilterStatement, ExtractorStatement,
            SinkStatement, SamplerContent {
        private final Condition condition;
        private final List<FilterStatement> thenBranch;
        private final List<FilterStatement> elseBranch;

        public IfBlock(final Condition condition,
                       final List<FilterStatement> thenBranch,
                       final List<FilterStatement> elseBranch) {
            this.condition = condition;
            this.thenBranch = Collections.unmodifiableList(thenBranch);
            this.elseBranch = elseBranch != null
                ? Collections.unmodifiableList(elseBranch) : Collections.emptyList();
        }
    }

    // ==================== Conditions ====================

    public interface Condition {
    }

    @Getter
    public static final class ComparisonCondition implements Condition {
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
    public static final class LogicalCondition implements Condition {
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
    public static final class NotCondition implements Condition {
        private final Condition inner;

        public NotCondition(final Condition inner) {
            this.inner = inner;
        }
    }

    @Getter
    public static final class ExprCondition implements Condition {
        private final ValueAccess expr;
        private final String castType;

        public ExprCondition(final ValueAccess expr, final String castType) {
            this.expr = expr;
            this.castType = castType;
        }
    }

    // ==================== Value access ====================

    @Getter
    public static final class ValueAccess {
        private final List<String> segments;
        private final boolean parsedRef;
        private final boolean logRef;
        private final boolean processRegistryRef;
        private final boolean stringLiteral;
        private final boolean numberLiteral;
        private final List<ValueAccessSegment> chain;
        private final String functionCallName;
        private final List<FunctionArg> functionCallArgs;
        private final List<ValueAccess> concatParts;
        private final ValueAccess parenInner;
        private final String parenCast;

        public ValueAccess(final List<String> segments,
                           final boolean parsedRef,
                           final boolean logRef,
                           final List<ValueAccessSegment> chain) {
            this(segments, parsedRef, logRef, false, false, false,
                chain, null, Collections.emptyList(),
                Collections.emptyList(), null, null);
        }

        public ValueAccess(final List<String> segments,
                           final boolean parsedRef,
                           final boolean logRef,
                           final boolean processRegistryRef,
                           final boolean stringLiteral,
                           final boolean numberLiteral,
                           final List<ValueAccessSegment> chain,
                           final String functionCallName,
                           final List<FunctionArg> functionCallArgs) {
            this(segments, parsedRef, logRef, processRegistryRef,
                stringLiteral, numberLiteral, chain,
                functionCallName, functionCallArgs,
                Collections.emptyList(), null, null);
        }

        public ValueAccess(final List<String> segments,
                           final boolean parsedRef,
                           final boolean logRef,
                           final boolean processRegistryRef,
                           final boolean stringLiteral,
                           final boolean numberLiteral,
                           final List<ValueAccessSegment> chain,
                           final String functionCallName,
                           final List<FunctionArg> functionCallArgs,
                           final List<ValueAccess> concatParts,
                           final ValueAccess parenInner,
                           final String parenCast) {
            this.segments = Collections.unmodifiableList(segments);
            this.parsedRef = parsedRef;
            this.logRef = logRef;
            this.processRegistryRef = processRegistryRef;
            this.stringLiteral = stringLiteral;
            this.numberLiteral = numberLiteral;
            this.chain = chain != null
                ? Collections.unmodifiableList(chain) : Collections.emptyList();
            this.functionCallName = functionCallName;
            this.functionCallArgs = functionCallArgs != null
                ? Collections.unmodifiableList(functionCallArgs) : Collections.emptyList();
            this.concatParts = concatParts != null
                ? Collections.unmodifiableList(concatParts) : Collections.emptyList();
            this.parenInner = parenInner;
            this.parenCast = parenCast;
        }

        public String toPathString() {
            return String.join(".", segments);
        }
    }

    @Getter
    public static final class FunctionArg {
        private final ValueAccess value;
        private final String castType;

        public FunctionArg(final ValueAccess value, final String castType) {
            this.value = value;
            this.castType = castType;
        }
    }

    public interface ValueAccessSegment {
    }

    @Getter
    public static final class FieldSegment implements ValueAccessSegment {
        private final String name;
        private final boolean safeNav;

        public FieldSegment(final String name, final boolean safeNav) {
            this.name = name;
            this.safeNav = safeNav;
        }
    }

    @Getter
    public static final class MethodSegment implements ValueAccessSegment {
        private final String name;
        private final List<FunctionArg> arguments;
        private final boolean safeNav;

        public MethodSegment(final String name, final List<FunctionArg> arguments,
                             final boolean safeNav) {
            this.name = name;
            this.arguments = arguments != null
                ? Collections.unmodifiableList(arguments) : Collections.emptyList();
            this.safeNav = safeNav;
        }
    }

    @Getter
    public static final class IndexSegment implements ValueAccessSegment {
        private final int index;

        public IndexSegment(final int index) {
            this.index = index;
        }
    }

    // ==================== Condition values ====================

    public interface ConditionValue {
    }

    @Getter
    public static final class StringConditionValue implements ConditionValue {
        private final String value;

        public StringConditionValue(final String value) {
            this.value = value;
        }
    }

    @Getter
    public static final class NumberConditionValue implements ConditionValue {
        private final double value;

        public NumberConditionValue(final double value) {
            this.value = value;
        }
    }

    @Getter
    public static final class BoolConditionValue implements ConditionValue {
        private final boolean value;

        public BoolConditionValue(final boolean value) {
            this.value = value;
        }
    }

    public static final class NullConditionValue implements ConditionValue {
    }

    @Getter
    public static final class ValueAccessConditionValue implements ConditionValue {
        private final ValueAccess value;
        private final String castType;

        public ValueAccessConditionValue(final ValueAccess value, final String castType) {
            this.value = value;
            this.castType = castType;
        }
    }

    @Getter
    public static final class FunctionCallConditionValue implements ConditionValue {
        private final String functionName;
        private final List<String> arguments;

        public FunctionCallConditionValue(final String functionName, final List<String> arguments) {
            this.functionName = functionName;
            this.arguments = Collections.unmodifiableList(arguments);
        }
    }

    // ==================== Enums ====================

    public enum CompareOp {
        EQ, NEQ, GT, LT, GTE, LTE
    }

    public enum LogicalOp {
        AND, OR
    }

    private LALScriptModel() {
        this.statements = Collections.emptyList();
    }
}
