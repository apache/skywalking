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

package org.apache.skywalking.oal.v2.generator;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.skywalking.oal.v2.metadata.FilterMatchers;
import org.apache.skywalking.oal.v2.model.FilterExpression;
import org.apache.skywalking.oal.v2.model.FilterOperator;
import org.apache.skywalking.oal.v2.model.FilterValue;
import org.apache.skywalking.oal.v2.util.TypeCastUtil;
import org.apache.skywalking.oap.server.core.analysis.metrics.expression.BooleanMatch;
import org.apache.skywalking.oap.server.core.analysis.metrics.expression.BooleanNotEqualMatch;
import org.apache.skywalking.oap.server.core.analysis.metrics.expression.ContainMatch;
import org.apache.skywalking.oap.server.core.analysis.metrics.expression.ExactInMatch;
import org.apache.skywalking.oap.server.core.analysis.metrics.expression.GreaterEqualMatch;
import org.apache.skywalking.oap.server.core.analysis.metrics.expression.GreaterMatch;
import org.apache.skywalking.oap.server.core.analysis.metrics.expression.InMatch;
import org.apache.skywalking.oap.server.core.analysis.metrics.expression.LessEqualMatch;
import org.apache.skywalking.oap.server.core.analysis.metrics.expression.LessMatch;
import org.apache.skywalking.oap.server.core.analysis.metrics.expression.LikeMatch;
import org.apache.skywalking.oap.server.core.analysis.metrics.expression.NotContainMatch;
import org.apache.skywalking.oap.server.core.analysis.metrics.expression.NotEqualMatch;
import org.apache.skywalking.oap.server.core.analysis.metrics.expression.NumberMatch;
import org.apache.skywalking.oap.server.core.analysis.metrics.expression.StringMatch;

/**
 * A filter, or a condition argument such as the one of {@code percent(status == true)}, as the matcher call the
 * template emits: the matcher {@link FilterMatchers} holds for the operator and the kind of value, the attribute's getter
 * chain ({@link SourceAttribute}) and the value as Java source. A value is fitted to the stock matchers only; a matcher
 * registered under an operator's name in place of a stock one gets the value and the attribute as written.
 */
final class FilterCompiler {

    /**
     * The matchers of {@code ==}, {@code !=}, {@code >}, {@code >=}, {@code <} and {@code <=} with a number; a filter
     * number is fitted to these only.
     */
    private static final Set<Class<?>> STOCK_NUMBER_MATCHERS = new HashSet<>(Arrays.asList(
        NumberMatch.class, NotEqualMatch.class, GreaterMatch.class, GreaterEqualMatch.class, LessMatch.class,
        LessEqualMatch.class));

    /**
     * Every matcher OAL ships; one registered in place of any of them gets a filter value as written.
     */
    private static final Set<Class<?>> STOCK_MATCHERS = Stream.concat(STOCK_NUMBER_MATCHERS.stream(), Stream.<Class<?>>of(
        StringMatch.class, LikeMatch.class, ContainMatch.class, NotContainMatch.class, InMatch.class, ExactInMatch.class,
        BooleanMatch.class, BooleanNotEqualMatch.class)).collect(Collectors.toSet());

    private final String sourcePackage;

    FilterCompiler(String sourcePackage) {
        this.sourcePackage = sourcePackage;
    }

    /**
     * A value is rejected only where its comparison is certain never to hold, or to change the value, and the field's
     * type is plain to see.
     *
     * @return the matcher call of the filter
     */
    CodeGenModel.FilterExpressionV2 compile(FilterExpression filter, Class<?> sourceClass) {
        final String condition = filter.getSourceText().isEmpty()
            ? filter.toString() : filter.getSourceText().replaceFirst("^\\.", "");
        if (filter.getCastType().isPresent()) {
            throw unsupportedCast(filter.getCastType().get(), condition);
        }
        final FilterValue written = filter.getValue().getWrittenForm() != null
            ? filter.getValue().getWrittenForm() : filter.getValue();
        final FilterMatchers.MatcherInfo writtenInfo = matcherOf(filter.getOperator(), written, filter);
        final FilterMatchers.MatcherInfo readInfo = matcherOf(filter.getOperator(), filter.getValue(), filter);
        if (writtenInfo != null && (!STOCK_MATCHERS.contains(writtenInfo.getMatcher())
            || readInfo != null && !STOCK_MATCHERS.contains(readInfo.getMatcher()))) {
            // a matcher registered in place of a stock one, whether the value as written or as read chooses it: the call
            // of the value as written, so a registered matcher is called only as the kind of value written chooses
            SourceAttribute.checkKeyedPath(filter.getFieldName(), true, writtenInfo.isBooleanType(), condition);
            return registeredMatcherFilter(filter, written, writtenInfo, sourceClass);
        }
        if (writtenInfo == null && readInfo != null && !STOCK_MATCHERS.contains(readInfo.getMatcher())) {
            // the value as written chooses no matcher (100L beside >): no registered one gets it as read
            throw new IllegalArgumentException(condition + ": the value as written takes no "
                + readInfo.getMatcher().getSimpleName() + ", which is registered in place of the stock matcher; write it "
                + "without an L or a _");
        }
        if (filter.getValue().getRefusal() != null) {
            throw new IllegalArgumentException(condition + ": " + filter.getValue().getRefusal());
        }
        FilterMatchers.MatcherInfo matcherInfo = FilterMatchers.INSTANCE.find(
            mapOperatorToExpressionType(filter.getOperator(), filter.getValue(), filter));
        SourceAttribute.checkKeyedPath(filter.getFieldName(), true, matcherInfo.isBooleanType(), condition);
        final SourceAttribute attribute = SourceAttribute.resolve(
            sourceClass, filter.getFieldName(), matcherInfo.isBooleanType());
        String left = "source." + attribute.getAccessor();
        final Class<?> type = attribute.getType();
        final FilterValue value = filter.getValue();
        final Class<?> matcher = matcherInfo.getMatcher();
        String right = filterValue(
            filter, type, matcher, condition + ": " + SourceAttribute.describe(sourceClass, filter.getFieldName()));
        if (matcher == InMatch.class && value.isArray() && StringComparison.exactList(value.asArray(), type)) {
            // the stock InMatch is registered: the stock exact membership is bound itself, never one registered under
            // its name
            matcherInfo = new FilterMatchers.MatcherInfo(ExactInMatch.class, false);
        }
        if (StringComparison.asObjects(matcherInfo.getMatcher(), value, type)) {
            left = StringComparison.asObject(left);
            right = StringComparison.asObject(right);
        }
        // Verbatim ANTLR slice from the parser — e.g.
        // "filter(detectPoint == DetectPoint.SERVER)". Used as the
        // captured filter record's sourceText so the UI matches against
        // the original .oal source byte-for-byte.
        return CodeGenModel.FilterExpressionV2.builder()
            .expressionObject(matcherInfo.getMatcher().getName())
            .left(left)
            .right(right)
            .sourceText(filter.getSourceText())
            .build();
    }

    /**
     * A value a cast outside from() cannot parse would throw out of the whole dispatch, stopping the source's other
     * metrics: until a cast can fail for its own rule alone, only from() takes one, as the shipped core.oal does behind a
     * null check.
     */
    static IllegalArgumentException unsupportedCast(String castType, String what) {
        return new IllegalArgumentException(
            what + ": a cast such as " + castType + " is supported in from() only, not in a filter or a function argument");
    }

    /**
     * @return the matcher held for the operator and the kind of value; null where there is none (a number written with
     *     an L, which is a string as written, beside {@code >})
     */
    private static FilterMatchers.MatcherInfo matcherOf(FilterOperator op, FilterValue value, FilterExpression filter) {
        try {
            return FilterMatchers.INSTANCE.find(mapOperatorToExpressionType(op, value, filter));
        } catch (IllegalArgumentException none) {
            return null;
        }
    }

    /**
     * A filter whose matcher, chosen by the kind of the value as written, is registered in place of a stock one: the
     * call of the attribute as written ({@link SourceAttribute#asWritten}) and the value as written (no escape decoded,
     * a dotted text naming a constant).
     */
    private CodeGenModel.FilterExpressionV2 registeredMatcherFilter(FilterExpression filter, FilterValue written,
                                                                    FilterMatchers.MatcherInfo matcherInfo,
                                                                    Class<?> sourceClass) {
        final SourceAttribute attribute = SourceAttribute.asWritten(
            sourceClass, filter.getFieldName(), matcherInfo.isBooleanType());
        final String right;
        if (written.isNumber()) {
            right = buildNumberLiteral(written.asNumber(), attribute.getType());
        } else if (written.isString()) {
            right = "\"" + written.asString() + "\"";
        } else if (written.isEnum()) {
            right = constantReference(written.asEnum());
        } else if (written.isBoolean()) {
            right = String.valueOf(written.asBoolean());
        } else if (written.isNull()) {
            right = "null";
        } else {
            final StringBuilder sb = new StringBuilder("new Object[]{");
            boolean first = true;
            for (final Object item : written.asArray()) {
                sb.append(first ? "" : ", ");
                first = false;
                if (item instanceof String && ((String) item).indexOf('.') >= 0) {
                    sb.append(constantReference((String) item));
                } else if (item instanceof String) {
                    sb.append('"').append(item).append('"');
                } else {
                    sb.append(item);
                }
            }
            right = sb.append('}').toString();
        }
        return CodeGenModel.FilterExpressionV2.builder()
            .expressionObject(matcherInfo.getMatcher().getName())
            .left("source." + attribute.getAccessor())
            .right(right)
            .sourceText(filter.getSourceText())
            .build();
    }

    /**
     * @param matcher the matcher registered for the operator. The value is fitted to the stock one: a registered
     *                replacement gets the value with no check of what the stock one matches.
     */
    private String filterValue(FilterExpression filter, Class<?> type, Class<?> matcher, String what) {
        final FilterOperator op = filter.getOperator();
        final FilterValue value = filter.getValue();
        if (value.isNumber()) {
            final Number number = value.asNumber();
            if (!STOCK_NUMBER_MATCHERS.contains(matcher)) {
                return buildNumberLiteral(number, type);
            }
            checkIntRange(number, type, what);
            if ((number instanceof Long || number instanceof Integer) && type != long.class && type != double.class
                && (number.longValue() < Integer.MIN_VALUE || number.longValue() > Integer.MAX_VALUE)) {
                // Javassist would read it as an int and wrap it; as a long it compares only with a long, or fails.
                return number + "L";
            }
            return buildNumberLiteral(number, type == null ? null : promoted(type));
        }
        if (value.isString()) {
            if (op == FilterOperator.LIKE) {
                if (matcher == LikeMatch.class) {
                    checkLikePattern(StringComparison.likePattern(value.asString()), what);
                }
            } else if (op == FilterOperator.CONTAIN || op == FilterOperator.NOT_CONTAIN) {
                if (matcher == ContainMatch.class || matcher == NotContainMatch.class) {
                    return StringComparison.containLiteral(value.asString());
                }
            } else if ((matcher == StringMatch.class || matcher == NotEqualMatch.class)
                && type != null && type.isEnum()) {
                throw new IllegalArgumentException(
                    what + " is of type " + type.getSimpleName() + ", which never equals a string; compare it with a "
                        + "constant, such as " + type.getSimpleName() + ".NAME");
            }
            return TypeCastUtil.toJavaSource(value.asString());
        }
        if (value.isEnum()) {
            return constantReference(value.asEnum());
        }
        if (value.isArray()) {
            return arrayValue(value.asArray(), type, matcher == InMatch.class, what);
        }
        if (value.isBoolean()) {
            return String.valueOf(value.asBoolean());
        }
        if (value.isNull()) {
            return "null";
        }
        throw new IllegalArgumentException("Unknown filter value type: " + value);
    }

    /**
     * A list of whole numbers, as {@code in [404, 500]} of the OAL guide, takes {@code InMatch}'s {@code long[]}
     * overloads: an int in an {@code Object[]} is bytecode the JVM rejects. Any other list is an {@code Object[]}.
     *
     * @param stock the stock {@code InMatch} is registered; a replacement gets an {@code Object[]}, unchecked.
     */
    private String arrayValue(List<?> items, Class<?> type, boolean stock, String what) {
        boolean numbers = stock && !items.isEmpty();
        for (Object item : items) {
            numbers &= item instanceof Long || item instanceof Integer;
        }
        if (numbers) {
            final StringBuilder sb = new StringBuilder("new long[]{");
            for (int i = 0; i < items.size(); i++) {
                checkIntRange((Number) items.get(i), type, what);
                sb.append(i == 0 ? "" : ", ").append(((Number) items.get(i)).longValue()).append('L');
            }
            return sb.append("}").toString();
        }
        StringBuilder sb = new StringBuilder("new Object[]{");
        boolean firstItem = true;
        for (Object item : items) {
            if (!firstItem) {
                sb.append(", ");
            }
            firstItem = false;
            if (item instanceof String && !stock) {
                sb.append(TypeCastUtil.toJavaSource((String) item));
            } else if (item instanceof String) {
                if (type != null && Collection.class.isAssignableFrom(type)) {
                    throw new IllegalArgumentException(
                        what + " is a " + type.getSimpleName() + ", which never equals the string \"" + item
                            + "\"; contain looks for a string in it");
                }
                if (type != null && !type.isAssignableFrom(String.class)) {
                    // As "Layer.MESH", a string, not the constant: a field that holds no string never equals it.
                    throw new IllegalArgumentException(
                        what + " is of type " + type.getSimpleName() + ", which never equals the string \"" + item + "\"");
                }
                if (type == null && ((String) item).indexOf('.') >= 0) {
                    // A quoted string with a dot names a constant as written (registeredMatcherFilter); as a string it
                    // is taken only where the field's type is plain to see.
                    throw new IllegalArgumentException(
                        what + ": the string \"" + item + "\" is compared only with a field whose type, read from its "
                            + "public getter, may hold a string; a keyed attribute's is not seen");
                }
                sb.append(TypeCastUtil.toJavaSource((String) item));
            } else if (item instanceof FilterValue && ((FilterValue) item).isEnum()) {
                final String text = ((FilterValue) item).asEnum();
                sb.append(constantReference(text));
            } else {
                sb.append(item);
            }
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Render a whole number as a literal of the long or double attribute it is compared with. Each comparison matcher
     * has one overload per type, such as {@code match(long, long)}, and Javassist does not widen an argument, so a bare
     * {@code 0} against a long attribute would look for {@code match(long, int)}. The literal itself carries the type,
     * {@code 0L} or {@code 0.0}: Javassist reads the number inside a cast such as {@code (long)(3000000000)} as an int
     * and overflows it. Any other attribute or number keeps the literal as written.
     */
    static String buildNumberLiteral(Number number, Class<?> attributeType) {
        boolean wholeNumber = number instanceof Long || number instanceof Integer;
        if (wholeNumber && attributeType == long.class) {
            return number + "L";
        }
        if (wholeNumber && attributeType == double.class) {
            return number + ".0";
        }
        return String.valueOf(number);
    }

    /**
     * Map filter operator and value type to matcher expression type.
     *
     * <p>The expression type string is used to look up the appropriate matcher class
     * from {@link FilterMatchers}.
     *
     * @param filterExpr filter expression the operator and value are of, for the message
     * @return matcher type string (e.g., "stringMatch", "greaterMatch")
     */
    private static String mapOperatorToExpressionType(FilterOperator op, FilterValue value,
                                                      FilterExpression filterExpr) {

        if (value.isBoolean()) {
            switch (op) {
                case EQUAL: return "booleanMatch";
                case NOT_EQUAL: return "booleanNotEqualMatch";
                default: throw new IllegalArgumentException("Unsupported boolean operator: " + op);
            }
        } else if (value.isNumber()) {
            switch (op) {
                case EQUAL: return "numberMatch";
                case NOT_EQUAL: return "notEqualMatch";
                case GREATER: return "greaterMatch";
                case LESS: return "lessMatch";
                case GREATER_EQUAL: return "greaterEqualMatch";
                case LESS_EQUAL: return "lessEqualMatch";
                default: throw new IllegalArgumentException("Unsupported number operator: " + op);
            }
        } else if (value.isString()) {
            switch (op) {
                case EQUAL: return "stringMatch";
                case NOT_EQUAL: return "notEqualMatch";
                case LIKE: return "likeMatch";
                case CONTAIN: return "containMatch";
                case NOT_CONTAIN: return "notContainMatch";
                default: throw new IllegalArgumentException("Unsupported string operator: " + op);
            }
        } else if (value.isArray()) {
            if (op != FilterOperator.IN) {
                throw new IllegalArgumentException("Unsupported list operator: " + op);
            }
            return "inMatch";
        } else if (value.isNull()) {
            switch (op) {
                case EQUAL: return "stringMatch";
                case NOT_EQUAL: return "notEqualMatch";
                default: throw new IllegalArgumentException("Unsupported null operator: " + op);
            }
        } else if (value.isEnum()) {
            // Enum comparisons use string matching (e.g., type == RequestType.MQ)
            switch (op) {
                case EQUAL: return "stringMatch";
                case NOT_EQUAL: return "notEqualMatch";
                default: throw new IllegalArgumentException("Unsupported enum operator: " + op);
            }
        }

        throw new IllegalArgumentException("Unsupported filter: " + filterExpr);
    }

    /**
     * {@code byte}, {@code short} and {@code char} compare as {@code int}, as Java promotes them.
     */
    private static Class<?> promoted(Class<?> type) {
        return type == byte.class || type == short.class || type == char.class ? int.class : type;
    }

    /**
     * {@code TYPE.NAME} names a constant of the class {@code TYPE} of the source package, which Javassist resolves, or
     * copies when it is a compile-time constant.
     */
    private String constantReference(String constant) {
        return sourcePackage + constant;
    }

    /**
     * {@code LikeMatch} matches a prefix ({@code abc%}), a suffix ({@code %abc}) or a part ({@code %abc%}). Any other
     * pattern never matches, or fails each event, as {@code %} alone does.
     */
    private static void checkLikePattern(String pattern, String condition) {
        if (pattern.length() < 2 || !pattern.startsWith("%") && !pattern.endsWith("%")) {
            throw new IllegalArgumentException(
                condition + ": a like pattern starts or ends with %, such as \"abc%\", \"%abc\" or \"%abc%\", and is "
                    + "more than % alone; compare with == for an exact match");
        }
    }

    /**
     * Javassist reads a number literal compared with an int field as an int, so one out of its range overflows.
     */
    private static void checkIntRange(Number number, Class<?> type, String what) {
        if (type == null || promoted(type) != int.class) {
            return;
        }
        if (number.longValue() < Integer.MIN_VALUE || number.longValue() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(what + " is of type int, and " + number + " is out of its range");
        }
    }
}
