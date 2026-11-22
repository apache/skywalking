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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
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

    /**
     * @return whether the script has a {@code sink} block on any branch, so it may save a log
     */
    public boolean hasSink() {
        return anyStatement(statements, stmt -> stmt instanceof SinkBlock);
    }

    /**
     * @return whether an {@code extractor} on any branch has a {@code layer} statement
     */
    public boolean assignsLayer() {
        return anyStatement(statements, stmt -> stmt instanceof ExtractorBlock
            && anyStatement(((ExtractorBlock) stmt).getStatements(), s -> s instanceof FieldAssignment
                && ((FieldAssignment) s).getFieldType() == FieldType.LAYER));
    }

    /**
     * Whether a statement of a block, or one nested in its {@code if} blocks, matches.
     */
    private static boolean anyStatement(final List<?> statements, final Predicate<Object> match) {
        for (final Object stmt : statements) {
            if (match.test(stmt)) {
                return true;
            }
            if (stmt instanceof IfBlock) {
                final IfBlock ifBlock = (IfBlock) stmt;
                if (anyStatement(ifBlock.getThenBranch(), match) || anyStatement(ifBlock.getElseBranch(), match)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ==================== Filter statements ====================

    public interface FilterStatement {
        /**
         * 1-based source line number of this AST statement, or {@code 0} when
         * the statement was synthesised (e.g. a default sink). Threaded from
         * {@code ctx.getStart().getLine()} at parse time.
         */
        default int getSourceLine() {
            return 0;
        }

        /**
         * Verbatim source text of the original DSL statement
         * (e.g. {@code "tag 'status.code': parsed.code"}). The dsl-debugging
         * codegen pairs this with {@link #getSourceLine()} on every
         * statement-level capture so the UI can render
         * "line N: {sourceText}" without round-tripping back to the source
         * file. Returns the empty string when the statement carries no
         * source-text origin (synthesised statements).
         */
        default String getSourceText() {
            return "";
        }
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
        private int sourceLine;
        private String sourceText = "";

        public DefStatement(final String varName,
                            final ValueAccess initializer,
                            final String castType) {
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
        private int sourceLine;
        private String sourceText = "";

        public FieldAssignment(final FieldType fieldType,
                               final ValueAccess value,
                               final String castType,
                               final String formatPattern) {
            this.fieldType = fieldType;
            this.value = value;
            this.castType = castType;
            this.formatPattern = formatPattern;
        }

        public FieldAssignment withSource(final int line, final String text) {
            this.sourceLine = line;
            this.sourceText = text == null ? "" : text;
            return this;
        }
    }

    @Getter
    public static final class OutputFieldAssignment implements ExtractorStatement, FilterStatement {
        private final String fieldName;
        private final ValueAccess value;
        private final String castType;
        private int sourceLine;
        private String sourceText = "";

        public OutputFieldAssignment(final String fieldName,
                                     final ValueAccess value,
                                     final String castType) {
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

    public enum FieldType {
        SERVICE, INSTANCE, ENDPOINT, LAYER,
        TRACE_ID, SEGMENT_ID, SPAN_ID, TIMESTAMP
    }

    @Getter
    public static final class TagAssignment implements ExtractorStatement, FilterStatement {
        /** Each key's last value, at its first position, as a Groovy map literal keeps them. */
        private final Map<String, TagValue> tags;
        /** Every entry as written, all of which Groovy evaluated. */
        private final List<Map.Entry<String, TagValue>> entries;
        private int sourceLine;
        private String sourceText = "";

        public TagAssignment(final List<Map.Entry<String, TagValue>> entries) {
            this.entries = Collections.unmodifiableList(entries);
            final Map<String, TagValue> tags = new LinkedHashMap<>();
            for (final Map.Entry<String, TagValue> entry : entries) {
                tags.put(entry.getKey(), entry.getValue());
            }
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

    @Getter
    public static final class MetricsBlock implements ExtractorStatement, FilterStatement {
        /**
         * The name when written as a string literal, otherwise null: {@link #getNameValue()} is what runs.
         */
        private final String name;
        private final ValueAccess nameValue;
        private final String nameCast;
        private final ValueAccess timestampValue;
        private final String timestampCast;
        private final Map<String, TagValue> labels;
        private final ValueAccess value;
        private final String valueCast;
        /**
         * Every statement in source order, which they run in, as Groovy LAL ran them; the properties above are
         * each kind's last.
         */
        private final List<MetricsStatement> statements;
        private int sourceLine;
        private String sourceText = "";

        public MetricsBlock(final List<MetricsStatement> statements) {
            this.statements = Collections.unmodifiableList(statements);
            ValueAccess nameValue = null;
            String nameCast = null;
            ValueAccess timestampValue = null;
            String timestampCast = null;
            Map<String, TagValue> labels = Collections.emptyMap();
            ValueAccess value = null;
            String valueCast = null;
            for (final MetricsStatement statement : statements) {
                switch (statement.getKind()) {
                    case NAME:
                        nameValue = statement.getValue();
                        nameCast = statement.getCast();
                        break;
                    case TIMESTAMP:
                        timestampValue = statement.getValue();
                        timestampCast = statement.getCast();
                        break;
                    case LABELS:
                        labels = statement.getLabels();
                        break;
                    default:
                        value = statement.getValue();
                        valueCast = statement.getCast();
                        break;
                }
            }
            this.nameValue = nameValue;
            this.nameCast = nameCast;
            this.name = nameValue != null && nameValue.isStringLiteral() && nameValue.getChain().isEmpty()
                ? nameValue.getSegments().get(0) : null;
            this.timestampValue = timestampValue;
            this.timestampCast = timestampCast;
            this.labels = labels;
            this.value = value;
            this.valueCast = valueCast;
        }

        public MetricsBlock withSource(final int line, final String text) {
            this.sourceLine = line;
            this.sourceText = text == null ? "" : text;
            return this;
        }
    }

    public enum MetricsStatementKind {
        NAME, TIMESTAMP, LABELS, VALUE
    }

    /**
     * One statement of a metrics block: a value and its cast, or the labels.
     */
    @Getter
    public static final class MetricsStatement {
        private final MetricsStatementKind kind;
        private final ValueAccess value;
        private final String cast;
        /** The labels as written, a repeated key included. */
        private final List<Map.Entry<String, TagValue>> labelEntries;
        /** Each label key's last value. */
        private final Map<String, TagValue> labels;

        public MetricsStatement(final MetricsStatementKind kind, final ValueAccess value, final String cast,
                                final List<Map.Entry<String, TagValue>> labelEntries) {
            this.kind = kind;
            this.value = value;
            this.cast = cast;
            this.labelEntries = labelEntries != null
                ? Collections.unmodifiableList(labelEntries) : Collections.emptyList();
            final Map<String, TagValue> labels = new LinkedHashMap<>();
            for (final Map.Entry<String, TagValue> entry : this.labelEntries) {
                labels.put(entry.getKey(), entry.getValue());
            }
            this.labels = Collections.unmodifiableMap(labels);
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
    public static final class RateLimitBlock implements SamplerContent, FilterStatement {
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
        private final String castType;

        private InterpolationPart(final String literal, final ValueAccess expression, final String castType) {
            this.literal = literal;
            this.expression = expression;
            this.castType = castType;
        }

        public static InterpolationPart ofLiteral(final String text) {
            return new InterpolationPart(text, null, null);
        }

        public static InterpolationPart ofExpression(final ValueAccess expr, final String castType) {
            return new InterpolationPart(null, expr, castType);
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

    /**
     * Binary arithmetic / concat operator joining adjacent {@code concatParts}.
     * The list of operators aligns with the gaps between parts: for N parts there
     * are N-1 operators, where {@code concatOps[i]} joins {@code concatParts[i]}
     * and {@code concatParts[i + 1]}.  Precedence is already encoded by the parser
     * (mul-level expressions sit inside add-level parts). The {@code symbol}
     * field carries the Java source token so the codegen doesn't need a parallel
     * switch.
     */
    public enum BinaryOp {
        PLUS("+"),
        MINUS("-"),
        STAR("*"),
        SLASH("/");

        private final String symbol;

        BinaryOp(final String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }
    }

    @Getter
    public static final class ValueAccess {
        /** The literals the grammar reads as names. */
        static final Set<String> LITERAL_NAMES = Set.of("true", "false", "null");

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
        private final List<BinaryOp> concatOps;
        private final ValueAccess parenInner;
        private final String parenCast;

        public ValueAccess(final List<String> segments,
                           final boolean parsedRef,
                           final boolean logRef,
                           final List<ValueAccessSegment> chain) {
            this(segments, parsedRef, logRef, false, false, false,
                chain, null, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), null, null);
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
                Collections.emptyList(), Collections.emptyList(), null, null);
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
            this(segments, parsedRef, logRef, processRegistryRef,
                stringLiteral, numberLiteral, chain,
                functionCallName, functionCallArgs,
                concatParts, defaultPlusOps(concatParts),
                parenInner, parenCast);
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
                           final List<BinaryOp> concatOps,
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
            this.concatOps = concatOps != null
                ? Collections.unmodifiableList(concatOps) : Collections.emptyList();
            this.parenInner = parenInner;
            this.parenCast = parenCast;
        }

        public String toPathString() {
            return String.join(".", segments);
        }

        /**
         * @return the value a grouping without a cast holds ({@code (x)}), when nothing follows it; else null
         */
        public ValueAccess groupedInner() {
            return parenInner != null && parenCast == null && chain.isEmpty() ? parenInner : null;
        }

        /**
         * @return whether the value starts with a name, a def's or {@code true}, {@code false}, {@code null}, which a
         *     chain may follow
         */
        public boolean isBareIdentifier() {
            return !parsedRef && !logRef && !processRegistryRef && !stringLiteral && !numberLiteral
                && functionCallName == null && parenInner == null && concatParts.isEmpty();
        }

        /**
         * @return whether the value is a literal alone: a string, a number, {@code true}, {@code false} or
         *     {@code null}, nothing following it
         */
        public boolean isBareLiteral() {
            return chain.isEmpty() && (stringLiteral || numberLiteral
                || isBareIdentifier() && LITERAL_NAMES.contains(segments.get(0)));
        }

        private static List<BinaryOp> defaultPlusOps(final List<ValueAccess> parts) {
            if (parts == null || parts.size() <= 1) {
                return Collections.emptyList();
            }
            final BinaryOp[] ops = new BinaryOp[parts.size() - 1];
            for (int i = 0; i < ops.length; i++) {
                ops[i] = BinaryOp.PLUS;
            }
            return List.of(ops);
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
        /**
         * The literal as written ({@code 10000}, {@code 10000L}, {@code 1.5}, {@code 1e6}), which types it: a bare
         * {@code 10000} is an {@code int}, an {@code L}-suffixed one a {@code long}.
         */
        private final String literal;

        public NumberConditionValue(final String literal) {
            this.literal = literal;
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

    // ==================== Enums ====================

    public enum CompareOp {
        EQ("==", null), NEQ("!=", null), GT(">", "gt"), LT("<", "lt"), GTE(">=", "ge"), LTE("<=", "le");

        private final String symbol;
        private final String helper;

        CompareOp(final String symbol, final String helper) {
            this.symbol = symbol;
            this.helper = helper;
        }

        public String symbol() {
            return symbol;
        }

        /**
         * @return the runtime helper of an ordering ({@code h.lt}, ...), which a NaN fails; null for {@code ==} and
         *     {@code !=}
         */
        public String helper() {
            return helper == null ? null : "h." + helper;
        }

        /**
         * @param order the left side's order against the right: negative, zero or positive
         * @return whether the comparison holds for that order
         */
        public boolean holds(final int order) {
            switch (this) {
                case EQ:
                    return order == 0;
                case NEQ:
                    return order != 0;
                case GT:
                    return order > 0;
                case LT:
                    return order < 0;
                case GTE:
                    return order >= 0;
                case LTE:
                    return order <= 0;
                default:
                    throw new IllegalStateException("Unhandled comparison " + this);
            }
        }
    }

    public enum LogicalOp {
        AND, OR
    }

    private LALScriptModel() {
        this.statements = Collections.emptyList();
    }
}
