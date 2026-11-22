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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oal.v2.metadata.MetricsHolder;
import org.apache.skywalking.oal.v2.metadata.SourceColumn;
import org.apache.skywalking.oal.v2.metadata.SourceColumnsFactory;
import org.apache.skywalking.oal.v2.util.TypeCastUtil;
import org.apache.skywalking.oal.v2.model.FilterExpression;
import org.apache.skywalking.oal.v2.model.FunctionArgument;
import org.apache.skywalking.oal.v2.model.MetricDefinition;
import org.apache.skywalking.oap.server.core.analysis.metrics.HistogramMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Arg;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.ConstOne;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.DefaultValue;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Entrance;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.SourceFrom;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;

/**
 * Enriches MetricDefinition with metadata needed for code generation.
 *
 * <p>This enricher transforms a parsed {@link MetricDefinition} into a complete
 * {@link CodeGenModel} by:
 * <ol>
 *   <li>Looking up the metrics function class (e.g., LongAvgMetrics)</li>
 *   <li>Extracting source columns/fields from the source class</li>
 *   <li>Finding the entrance method annotated with {@code @Entrance}</li>
 *   <li>Building entrance method arguments with type casts and expressions</li>
 *   <li>Collecting persistent fields from {@code @Column} annotations</li>
 *   <li>Generating serialization field lists grouped by type</li>
 * </ol>
 *
 * <p>Example input (MetricDefinition from OAL):
 * <pre>
 * service_resp_time = from(Service.latency).longAvg();
 * </pre>
 *
 * <p>Example output (CodeGenModel):
 * <ul>
 *   <li>metricsClassName = "LongAvgMetrics"</li>
 *   <li>entranceMethod.methodName = "combine"</li>
 *   <li>fieldsFromSource = [entityId, name, ...]</li>
 *   <li>persistentFields = [summation, count, value]</li>
 * </ul>
 *
 * @see CodeGenModel
 * @see OALClassGeneratorV2
 */
@Slf4j
public class MetricDefinitionEnricher {

    private final String sourcePackage;
    private final String metricsClassPackage;
    private final FilterCompiler filterCompiler;

    public MetricDefinitionEnricher(String sourcePackage, String metricsClassPackage) {
        this.sourcePackage = sourcePackage;
        this.metricsClassPackage = metricsClassPackage;
        this.filterCompiler = new FilterCompiler(sourcePackage);
    }

    /**
     * Enrich a MetricDefinition with metadata for code generation.
     *
     * @param metric parsed metric definition from OAL script
     * @return enriched code generation model ready for template processing
     */
    public CodeGenModel enrich(MetricDefinition metric) {
        try {
            return doEnrich(metric);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(metric.getLocation() + " " + metric.getName() + ": " + e.getMessage(), e);
        }
    }

    private CodeGenModel doEnrich(MetricDefinition metric) {
        // 1. Look up metrics function class
        Class<? extends Metrics> metricsClass = MetricsHolder.find(metric.getAggregationFunction().getName());
        String metricsClassName = metricsClass.getSimpleName();

        // 2. Get source columns
        List<SourceColumn> sourceColumns = SourceColumnsFactory.getColumns(metric.getSource().getName());
        List<CodeGenModel.SourceFieldV2> fieldsFromSource = convertSourceColumns(sourceColumns);

        // 3. Get source scope ID
        int sourceScopeId = DefaultScopeDefine.valueOf(metric.getSource().getName());
        Class<?> sourceClass = sourceClass(metric.getSource().getName());

        // 4. Find and process entrance method
        Method entranceMethod = findEntranceMethod(metricsClass);
        CodeGenModel.EntranceMethodV2 entranceMethodV2 =
            buildEntranceMethod(entranceMethod, metric, metricsClass, sourceClass);

        // 5. Collect persistent fields from metrics class
        List<CodeGenModel.DataFieldV2> persistentFields = collectPersistentFields(metricsClass);

        // 6. Generate serialization fields
        CodeGenModel.SerializeFieldsV2 serializeFields = generateSerializeFields(fieldsFromSource, persistentFields);

        // 7. Convert filter expressions to template format
        List<CodeGenModel.FilterExpressionV2> filterExpressions = convertFilters(metric.getFilters(), sourceClass);

        // 8. Build CodeGenModel
        return CodeGenModel.builder()
            .metricDefinition(metric)
            .varName(metric.getName())
            .metricsName(metricsNameFormat(metric.getName()))
            .metricsClassPackage(metricsClassPackage)
            .sourcePackage(sourcePackage)
            .tableName(metric.getTableName())
            .sourceName(metric.getSource().getName())
            .sourceScopeId(sourceScopeId)
            .from(CodeGenModel.FromStmtV2.builder()
                .sourceName(metric.getSource().getName())
                .sourceScopeId(sourceScopeId)
                // ANTLR Interval slice captured at parse time on the
                // metricStatement context (see OALListenerV2.exitMetricStatement)
                // — already byte-identical to what the operator wrote, no
                // runtime reconstruction needed at the capture call site.
                .fromText(metric.getFromText())
                .build())
            .functionName(metric.getAggregationFunction().getName())
            // Verbatim ANTLR slice from the parser — e.g. "cpm()", "percentile2(10)".
            // Captured byte-for-byte from the original .oal so the UI can match an
            // aggregation capture record against the source.
            .aggregationSourceText(metric.getAggregationFunction().getSourceText())
            // 1-based source line in core.oal — threaded into every dsl-debugging
            // probe call site so captured records carry an in-source pointer.
            .sourceLine(metric.getLocation().getLine())
            // Verbatim ANTLR slice of this metric's full statement — used as the
            // per-metric GateHolder's content so dsl-debugging records carry the
            // rule source inline.
            .metricSourceText(metric.getSourceText())
            .metricsClassName(metricsClassName)
            .filters(CodeGenModel.FiltersV2.builder()
                .filterExpressions(filterExpressions)
                .build())
            .filterExpressions(filterExpressions)
            .fieldsFromSource(fieldsFromSource)
            .persistentFields(persistentFields)
            .serializeFields(serializeFields)
            .entranceMethod(entranceMethodV2)
            .sourceDecorator(metric.getDecorator().orElse(null))
            .build();
    }

    private Class<?> sourceClass(String sourceName) {
        try {
            return Class.forName(sourcePackage + sourceName, false, MetricDefinitionEnricher.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("source " + sourceName + " is not a class in " + sourcePackage, e);
        }
    }

    /**
     * Convert SourceColumn metadata to SourceFieldV2 for code generation.
     *
     * @param sourceColumns source columns extracted from the source class
     * @return list of source fields ready for template processing
     */
    private List<CodeGenModel.SourceFieldV2> convertSourceColumns(List<SourceColumn> sourceColumns) {
        List<CodeGenModel.SourceFieldV2> fields = new ArrayList<>();
        for (SourceColumn column : sourceColumns) {
            fields.add(CodeGenModel.SourceFieldV2.builder()
                .fieldName(column.getFieldName())
                .columnName(column.getColumnName())
                .typeName(column.getType().getName())
                .type(column.getType())
                .isID(column.isID())
                .isShardingKey(column.isShardingKey())
                .shardingKeyIdx(column.getShardingKeyIdx())
                .length(column.getLength())
                .attribute(column.isAttribute())
                .build());
        }
        return fields;
    }

    /**
     * Find entrance method annotated with @Entrance.
     */
    private Method findEntranceMethod(Class<? extends Metrics> metricsClass) {
        Class<?> c = metricsClass;
        while (!c.equals(Object.class)) {
            for (Method method : c.getMethods()) {
                if (method.isAnnotationPresent(Entrance.class)) {
                    return method;
                }
            }
            c = c.getSuperclass();
        }
        throw new IllegalArgumentException("Can't find Entrance method in class: " + metricsClass.getName());
    }

    /**
     * Build entrance method information for dispatcher. The function's arguments fill its {@code @Expression},
     * {@code @Arg} and {@code @DefaultValue} parameters in order; one left over fails the compile, as it would be
     * ignored.
     */
    private CodeGenModel.EntranceMethodV2 buildEntranceMethod(Method entranceMethod, MetricDefinition metric,
                                                              Class<? extends Metrics> metricsClass,
                                                              Class<?> sourceClass) {
        CodeGenModel.EntranceMethodV2.EntranceMethodV2Builder builder = CodeGenModel.EntranceMethodV2.builder()
            .methodName(entranceMethod.getName());

        final String function = metric.getAggregationFunction().getName();
        final List<FunctionArgument> arguments = metric.getAggregationFunction().getArguments();
        List<Object> argsExpressions = new ArrayList<>();
        List<Integer> argTypes = new ArrayList<>();
        int funcArgIndex = 0;
        boolean readsSource = false;

        for (Parameter parameter : entranceMethod.getParameters()) {
            Class<?> parameterType = parameter.getType();
            Annotation[] parameterAnnotations = parameter.getAnnotations();

            if (parameterAnnotations == null || parameterAnnotations.length == 0) {
                throw new IllegalArgumentException(
                    "Entrance method:" + entranceMethod + " doesn't include the annotation.");
            }

            Annotation annotation = parameterAnnotations[0];

            if (annotation instanceof SourceFrom) {
                readsSource = true;
                argsExpressions.add(sourceValue(metric, sourceClass, parameterType));
                argTypes.add(2); // ATTRIBUTE_EXP_TYPE
            } else if (annotation instanceof ConstOne) {
                // Match V1 behavior: always wrap in type cast for consistency
                if (parameterType.equals(long.class)) {
                    argsExpressions.add("(long)(1)");
                } else if (parameterType.equals(int.class)) {
                    argsExpressions.add("(int)(1)");
                } else if (parameterType.equals(double.class)) {
                    argsExpressions.add("(double)(1)");
                } else if (parameterType.equals(float.class)) {
                    argsExpressions.add("(float)(1)");
                } else {
                    argsExpressions.add("1");
                }
                argTypes.add(1); // LITERAL_TYPE
            } else if (annotation instanceof org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Expression) {
                // A missing argument would leave the call short of one, and Javassist to pick a shorter overload.
                requireArgument(function, funcArgIndex, arguments, parameterType);
                FunctionArgument funcArg = arguments.get(funcArgIndex++);
                if (!funcArg.isExpression()) {
                    throw new IllegalArgumentException("Expected expression argument but got: " + funcArg);
                }
                // Template expects arg.expressionObject, arg.left, arg.right when argType >= 3
                argsExpressions.add(filterCompiler.compile(funcArg.asExpression(), sourceClass));
                argTypes.add(3); // EXPRESSION_TYPE
            } else if (annotation instanceof Arg) {
                requireArgument(function, funcArgIndex, arguments, parameterType);
                FunctionArgument funcArg = arguments.get(funcArgIndex++);
                argsExpressions.add(argumentValue(
                    function, funcArgIndex, funcArg, parameterType, sourceClass, false, ((Arg) annotation).min()));
                argTypes.add(funcArg.isLiteral() ? 1 : 2);
            } else if (annotation instanceof DefaultValue) {
                if (funcArgIndex < arguments.size()) {
                    FunctionArgument funcArg = arguments.get(funcArgIndex++);
                    argsExpressions.add(argumentValue(
                        function, funcArgIndex, funcArg, parameterType, sourceClass, true, Long.MIN_VALUE));
                    argTypes.add(funcArg.isLiteral() ? 1 : 2);
                } else {
                    argsExpressions.add(((DefaultValue) annotation).value());
                    argTypes.add(1); // LITERAL_TYPE
                }
            } else {
                throw new IllegalArgumentException(
                    "Entrance method:" + entranceMethod + " doesn't have expected annotation.");
            }
        }

        if (funcArgIndex < arguments.size()) {
            throw new IllegalArgumentException(
                function + "() takes " + funcArgIndex + " argument(s), but is given " + arguments.size()
                    + ": " + arguments.subList(funcArgIndex, arguments.size()) + " would be ignored");
        }
        if (!readsSource) {
            checkUnreadSourceAttribute(metric);
        }
        if (entranceMethod.getDeclaringClass() == HistogramMetrics.class) {
            checkHistogramBuckets(function, arguments);
        }

        return builder
            .argsExpressions(argsExpressions)
            .argTypes(argTypes)
            .build();
    }

    /**
     * The value of the from() attribute, for a {@code @SourceFrom} parameter.
     */
    private String sourceValue(MetricDefinition metric, Class<?> sourceClass, Class<?> parameterType) {
        final String function = metric.getAggregationFunction().getName();
        final List<String> attributes = metric.getSource().getAttributes();
        final String castType = metric.getSource().getCastType().orElse(null);
        if (attributes.isEmpty()) {
            if (castType != null) {
                return TypeCastUtil.withCast(castType, "source");
            }
            return TypeCastUtil.toParameter(null, parameterType, "source");
        }
        final SourceAttribute attribute = SourceAttribute.resolve(sourceClass, String.join(".", attributes), false);
        final String expression = "source." + attribute.getAccessor();
        final String what = "from(" + describeFrom(metric) + ")";
        if (castType != null) {
            return TypeCastUtil.withCast(castType, expression);
        }
        final Class<?> type = attribute.getType();
        requireNoFraction(type, parameterType, what, function);
        return TypeCastUtil.toParameter(type, parameterType, expression);
    }

    /**
     * The value of a function argument, for an {@code @Arg} or {@code @DefaultValue} parameter.
     *
     * @param constant the argument configures the metric, as a label limit does, so it is a literal: a value read from
     *                 each event would differ between the events merged into one metric, and the generated
     *                 serialization does not carry it to the node that merges the metric.
     * @param min      the least a number literal may be
     */
    private String argumentValue(String function, int position, FunctionArgument funcArg, Class<?> parameterType,
                                 Class<?> sourceClass, boolean constant, long min) {
        final String what = function + "() argument " + position;
        if (funcArg.isExpression()) {
            // Left out of the call, it would leave Javassist to pick another overload, or none.
            throw new IllegalArgumentException(
                what + " is of type " + parameterType.getSimpleName() + ", not the condition " + funcArg);
        }
        if (funcArg.isLongSuffixed()) {
            // Passed to Javassist as written, with no check of its value or range, for the overload it picks.
            return (String) funcArg.asLiteral();
        }
        if (funcArg.isLiteral()) {
            final Object literal = TypeCastUtil.integralAsLong(funcArg.asLiteral());
            if (literal instanceof Long && (Long) literal < min) {
                throw new IllegalArgumentException(what + " is " + literal + ", but must be at least " + min);
            }
            return literalOf(literal, what);
        }
        if (constant) {
            throw new IllegalArgumentException(
                what + " configures the metric, so it is a literal, not the attribute " + funcArg);
        }
        if (funcArg.getCastType().isPresent()) {
            throw FilterCompiler.unsupportedCast(funcArg.getCastType().get(), what + " " + funcArg);
        }
        SourceAttribute.checkKeyedPath(funcArg.asAttribute(), false, parameterType == boolean.class, what);
        final SourceAttribute attribute = SourceAttribute.resolve(
            sourceClass, funcArg.asAttribute(), parameterType == boolean.class);
        final String expression = "source." + attribute.getAccessor();
        requireNoFraction(attribute.getType(), parameterType, what + " " + funcArg, function);
        return TypeCastUtil.toParameter(attribute.getType(), parameterType, expression);
    }

    /**
     * Only a {@code @DefaultValue} parameter has a value of its own.
     */
    private static void requireArgument(String function, int index, List<FunctionArgument> arguments,
                                        Class<?> parameterType) {
        if (index >= arguments.size()) {
            throw new IllegalArgumentException(
                function + "() needs argument " + (index + 1) + ", of type " + parameterType.getSimpleName());
        }
    }

    /**
     * A literal argument as Java source. A number is written as is, which Javassist reads as an int. A string would be
     * pasted into the call as Java code, compiling only where it is code and running as such: it fails.
     */
    private static String literalOf(Object literal, String what) {
        if (literal instanceof Long) {
            final long number = (Long) literal;
            if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
                // Javassist reads a number written without an L as an int, whatever the parameter, and wraps it.
                throw new IllegalArgumentException(
                    what + " " + literal + " is out of the int range; a long is written with an L");
            }
            return String.valueOf(number);
        }
        if (literal instanceof String) {
            throw new IllegalArgumentException(
                what + " is the string \"" + literal + "\", which a function argument cannot be: a string literal would "
                    + "run as the Java code it spells");
        }
        return String.valueOf(literal);
    }

    /**
     * A function with no {@code @SourceFrom} parameter reads no value from the from() attribute. Such a script counts
     * every event of the source whatever the attribute, so it compiles, with a warning.
     */
    private void checkUnreadSourceAttribute(MetricDefinition metric) {
        if (!metric.getSource().getAttributes().isEmpty()) {
            // unread, the path still names only what a path may read (from(Service.name.bytes) is refused)
            SourceAttribute.resolve(sourceClass(metric.getSource().getName()),
                String.join(".", metric.getSource().getAttributes()), false);
        }
        if (metric.getSource().getCastType().isPresent() || !metric.getSource().getAttributes().isEmpty()) {
            log.warn("{} {}: {}() reads no value from the source, so from({}) has the effect of from({}.*)",
                     metric.getLocation(), metric.getName(), metric.getAggregationFunction().getName(),
                     describeFrom(metric), metric.getSource().getName());
        }
    }


    /**
     * {@code HistogramMetrics} names its buckets {@code step * i} up to {@code step * maxNumOfSteps}, in int.
     */
    private static void checkHistogramBuckets(String function, List<FunctionArgument> arguments) {
        if (arguments.size() < 2 || !arguments.get(0).isLiteral() || !arguments.get(1).isLiteral()
            || !(arguments.get(0).asLiteral() instanceof Number) || !(arguments.get(1).asLiteral() instanceof Number)) {
            return;
        }
        final long step = ((Number) arguments.get(0).asLiteral()).longValue();
        final long steps = ((Number) arguments.get(1).asLiteral()).longValue();
        if (step * steps > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                function + "(" + step + ", " + steps + "): the last bucket, " + step * steps
                    + ", is out of the int range");
        }
    }

    private static String describeFrom(MetricDefinition metric) {
        final List<String> attributes = metric.getSource().getAttributes();
        return metric.getSource().getCastType().orElse("") + metric.getSource().getName() + "."
            + (attributes.isEmpty() ? "*" : String.join(".", attributes));
    }

    /**
     * The generated cast of a double into a long or int parameter cuts the fraction off in silence. Any other value the
     * generated code cannot pass fails the compile in Javassist, or in the class load right after it.
     */
    private static void requireNoFraction(Class<?> valueType, Class<?> parameterType, String what, String function) {
        if ((valueType == double.class || valueType == float.class)
            && (parameterType == long.class || parameterType == int.class)) {
            throw new IllegalArgumentException(
                what + " is of type " + valueType.getSimpleName() + ", but " + function + "() takes "
                    + parameterType.getSimpleName() + ": the conversion would lose part of the value");
        }
    }

    /**
     * Collect persistent fields from metrics class @Column annotations.
     */
    private List<CodeGenModel.DataFieldV2> collectPersistentFields(Class<? extends Metrics> metricsClass) {
        List<CodeGenModel.DataFieldV2> persistentFields = new ArrayList<>();
        Class<?> c = metricsClass;
        while (!c.equals(Object.class)) {
            for (Field field : c.getDeclaredFields()) {
                Column column = field.getAnnotation(Column.class);
                if (column != null) {
                    persistentFields.add(CodeGenModel.DataFieldV2.builder()
                        .fieldName(field.getName())
                        .columnName(column.name())
                        .type(field.getType())
                        .typeName(field.getType().getName())
                        .build());
                }
            }
            c = c.getSuperclass();
        }
        return persistentFields;
    }

    /**
     * Generate serialization fields from source fields and persistent fields.
     */
    private CodeGenModel.SerializeFieldsV2 generateSerializeFields(
        List<CodeGenModel.SourceFieldV2> fieldsFromSource,
        List<CodeGenModel.DataFieldV2> persistentFields) {

        CodeGenModel.SerializeFieldsV2.SerializeFieldsV2Builder builder = CodeGenModel.SerializeFieldsV2.builder();
        CodeGenModel.SerializeFieldsV2 serializeFields = builder.build();

        // Add source fields
        for (CodeGenModel.SourceFieldV2 sourceField : fieldsFromSource) {
            String typeName = sourceField.getType().getSimpleName();
            switch (typeName) {
                case "int":
                    serializeFields.addIntField(sourceField.getFieldName());
                    break;
                case "double":
                    serializeFields.addDoubleField(sourceField.getFieldName());
                    break;
                case "String":
                    serializeFields.addStringField(sourceField.getFieldName());
                    break;
                case "long":
                    serializeFields.addLongField(sourceField.getFieldName());
                    break;
                default:
                    throw new IllegalStateException("Unexpected source field type: " + typeName);
            }
        }

        // Add persistent fields
        for (CodeGenModel.DataFieldV2 dataField : persistentFields) {
            Class<?> type = dataField.getType();
            if (type.equals(int.class)) {
                serializeFields.addIntField(dataField.getFieldName());
            } else if (type.equals(double.class)) {
                serializeFields.addDoubleField(dataField.getFieldName());
            } else if (type.equals(String.class)) {
                serializeFields.addStringField(dataField.getFieldName());
            } else if (type.equals(long.class)) {
                serializeFields.addLongField(dataField.getFieldName());
            } else if (StorageDataComplexObject.class.isAssignableFrom(type)) {
                serializeFields.addObjectField(dataField.getFieldName(), type.getName());
            } else {
                throw new IllegalStateException("Unexpected persistent field type: " + type);
            }
        }

        return serializeFields;
    }

    /**
     * Convert filter expressions to template-ready format.
     */
    private List<CodeGenModel.FilterExpressionV2> convertFilters(List<FilterExpression> filters, Class<?> sourceClass) {
        List<CodeGenModel.FilterExpressionV2> result = new ArrayList<>();
        for (FilterExpression filter : filters) {
            result.add(filterCompiler.compile(filter, sourceClass));
        }
        return result;
    }

    /**
     * Format metrics name (convert snake_case to PascalCase).
     */
    private String metricsNameFormat(String source) {
        source = firstLetterUpper(source);
        int idx;
        while ((idx = source.indexOf("_")) > -1) {
            source = source.substring(0, idx) + firstLetterUpper(source.substring(idx + 1));
        }
        return source;
    }

    private String firstLetterUpper(String source) {
        return source.substring(0, 1).toUpperCase() + source.substring(1);
    }
}
