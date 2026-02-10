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
import org.apache.skywalking.oal.v2.metadata.FilterMatchers;
import org.apache.skywalking.oal.v2.metadata.MetricsHolder;
import org.apache.skywalking.oal.v2.metadata.SourceColumn;
import org.apache.skywalking.oal.v2.metadata.SourceColumnsFactory;
import org.apache.skywalking.oal.v2.util.ClassMethodUtil;
import org.apache.skywalking.oal.v2.util.TypeCastUtil;
import org.apache.skywalking.oal.v2.model.FilterExpression;
import org.apache.skywalking.oal.v2.model.FilterOperator;
import org.apache.skywalking.oal.v2.model.FilterValue;
import org.apache.skywalking.oal.v2.model.FunctionArgument;
import org.apache.skywalking.oal.v2.model.MetricDefinition;
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

    public MetricDefinitionEnricher(String sourcePackage, String metricsClassPackage) {
        this.sourcePackage = sourcePackage;
        this.metricsClassPackage = metricsClassPackage;
    }

    /**
     * Enrich a MetricDefinition with metadata for code generation.
     *
     * @param metric parsed metric definition from OAL script
     * @return enriched code generation model ready for template processing
     */
    public CodeGenModel enrich(MetricDefinition metric) {
        // 1. Look up metrics function class
        Class<? extends Metrics> metricsClass = MetricsHolder.find(metric.getAggregationFunction().getName());
        String metricsClassName = metricsClass.getSimpleName();

        // 2. Get source columns
        List<SourceColumn> sourceColumns = SourceColumnsFactory.getColumns(metric.getSource().getName());
        List<CodeGenModel.SourceFieldV2> fieldsFromSource = convertSourceColumns(sourceColumns);

        // 3. Get source scope ID
        int sourceScopeId = DefaultScopeDefine.valueOf(metric.getSource().getName());

        // 4. Find and process entrance method
        Method entranceMethod = findEntranceMethod(metricsClass);
        CodeGenModel.EntranceMethodV2 entranceMethodV2 = buildEntranceMethod(entranceMethod, metric);

        // 5. Collect persistent fields from metrics class
        List<CodeGenModel.DataFieldV2> persistentFields = collectPersistentFields(metricsClass);

        // 6. Generate serialization fields
        CodeGenModel.SerializeFieldsV2 serializeFields = generateSerializeFields(fieldsFromSource, persistentFields);

        // 7. Convert filter expressions to template format
        List<CodeGenModel.FilterExpressionV2> filterExpressions = convertFilters(metric.getFilters());

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
                .build())
            .functionName(metric.getAggregationFunction().getName())
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
     * Build entrance method information for dispatcher.
     */
    private CodeGenModel.EntranceMethodV2 buildEntranceMethod(Method entranceMethod, MetricDefinition metric) {
        CodeGenModel.EntranceMethodV2.EntranceMethodV2Builder builder = CodeGenModel.EntranceMethodV2.builder()
            .methodName(entranceMethod.getName());

        List<Object> argsExpressions = new ArrayList<>();
        List<Integer> argTypes = new ArrayList<>();
        int funcArgIndex = 0;

        for (Parameter parameter : entranceMethod.getParameters()) {
            Class<?> parameterType = parameter.getType();
            Annotation[] parameterAnnotations = parameter.getAnnotations();

            if (parameterAnnotations == null || parameterAnnotations.length == 0) {
                throw new IllegalArgumentException(
                    "Entrance method:" + entranceMethod + " doesn't include the annotation.");
            }

            Annotation annotation = parameterAnnotations[0];

            if (annotation instanceof SourceFrom) {
                // Source attribute from OAL script
                // Handle nested attributes and map expressions
                List<String> attributes = metric.getSource().getAttributes();
                String expression = attributes.isEmpty()
                    ? "source"
                    : "source." + ClassMethodUtil.toGetMethod(attributes);
                String castType = metric.getSource().getCastType().orElse(null);
                // Cast to match parameter type if needed
                if (castType != null) {
                    expression = TypeCastUtil.withCast(castType, expression);
                } else if (parameterType.equals(int.class)) {
                    expression = "(int)(" + expression + ")";
                } else if (parameterType.equals(long.class)) {
                    expression = "(long)(" + expression + ")";
                } else if (parameterType.equals(double.class)) {
                    expression = "(double)(" + expression + ")";
                }
                argsExpressions.add(expression);
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
                // Expression argument - convert V2 filter to expression object
                // Template expects arg.expressionObject, arg.left, arg.right when argType >= 3
                if (funcArgIndex < metric.getAggregationFunction().getArguments().size()) {
                    FunctionArgument funcArg = metric.getAggregationFunction().getArguments().get(funcArgIndex++);
                    if (funcArg.isExpression()) {
                        FilterExpression filterExpr = funcArg.asExpression();
                        String matcherClass = getMatcherClassName(filterExpr);
                        String left = buildFilterLeft(filterExpr);
                        String right = buildFilterRight(filterExpr);
                        // Add as FilterExpressionV2 object for template access
                        argsExpressions.add(CodeGenModel.FilterExpressionV2.builder()
                            .expressionObject(matcherClass)
                            .left(left)
                            .right(right)
                            .build());
                        argTypes.add(3); // EXPRESSION_TYPE
                    } else {
                        throw new IllegalArgumentException("Expected expression argument but got: " + funcArg);
                    }
                }
            } else if (annotation instanceof Arg) {
                // Literal/attribute argument
                if (funcArgIndex < metric.getAggregationFunction().getArguments().size()) {
                    FunctionArgument funcArg = metric.getAggregationFunction().getArguments().get(funcArgIndex++);
                    if (funcArg.isLiteral()) {
                        argsExpressions.add(String.valueOf(funcArg.asLiteral()));
                        argTypes.add(1); // LITERAL_TYPE
                    } else if (funcArg.isAttribute()) {
                        argsExpressions.add("source." + ClassMethodUtil.toGetMethod(funcArg.asAttribute()) + "()");
                        argTypes.add(2); // ATTRIBUTE_EXP_TYPE
                    }
                }
            } else if (annotation instanceof DefaultValue) {
                // Use default or provided value
                if (funcArgIndex < metric.getAggregationFunction().getArguments().size()) {
                    FunctionArgument funcArg = metric.getAggregationFunction().getArguments().get(funcArgIndex++);
                    if (funcArg.isLiteral()) {
                        argsExpressions.add(String.valueOf(funcArg.asLiteral()));
                    } else {
                        argsExpressions.add(((DefaultValue) annotation).value());
                    }
                } else {
                    argsExpressions.add(((DefaultValue) annotation).value());
                }
                argTypes.add(1); // LITERAL_TYPE
            } else {
                throw new IllegalArgumentException(
                    "Entrance method:" + entranceMethod + " doesn't have expected annotation.");
            }
        }

        return builder
            .argsExpressions(argsExpressions)
            .argTypes(argTypes)
            .build();
    }

    /**
     * Get matcher class name from filter expression.
     */
    private String getMatcherClassName(FilterExpression filterExpr) {
        String expressionType = mapOperatorToExpressionType(filterExpr);
        FilterMatchers.MatcherInfo matcherInfo = FilterMatchers.INSTANCE.find(expressionType);
        return matcherInfo.getMatcher().getName();
    }

    /**
     * Build left side of filter expression.
     */
    private String buildFilterLeft(FilterExpression filterExpr) {
        FilterMatchers.MatcherInfo matcherInfo = FilterMatchers.INSTANCE.find(
            mapOperatorToExpressionType(filterExpr));
        String getter = matcherInfo.isBooleanType()
            ? ClassMethodUtil.toIsMethod(filterExpr.getFieldName())
            : ClassMethodUtil.toGetMethod(filterExpr.getFieldName());
        return "source." + getter + "()";
    }

    /**
     * Build right side of filter expression.
     */
    private String buildFilterRight(FilterExpression filterExpr) {
        FilterValue value = filterExpr.getValue();
        if (value.isNumber()) {
            return String.valueOf(value.asNumber());
        } else if (value.isString()) {
            return "\"" + value.asString() + "\"";
        } else if (value.isBoolean()) {
            return String.valueOf(value.asBoolean());
        } else if (value.isNull()) {
            return "null";
        } else if (value.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (Object item : value.asArray()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                if (item instanceof String) {
                    sb.append("\"").append(item).append("\"");
                } else {
                    sb.append(item);
                }
            }
            return sb.toString();
        }
        throw new IllegalArgumentException("Unknown filter value type: " + value);
    }

    /**
     * Map filter operator and value type to matcher expression type.
     *
     * <p>The expression type string is used to look up the appropriate matcher class
     * from {@link FilterMatchers}.
     *
     * @param filterExpr filter expression containing operator and value
     * @return matcher type string (e.g., "stringMatch", "greaterMatch")
     */
    private String mapOperatorToExpressionType(FilterExpression filterExpr) {
        FilterOperator op = filterExpr.getOperator();
        FilterValue value = filterExpr.getValue();

        if (value.isBoolean()) {
            return op == FilterOperator.EQUAL ? "booleanMatch" : "booleanNotEqualMatch";
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
            return "inMatch";
        } else if (value.isNull()) {
            return op == FilterOperator.EQUAL ? "stringMatch" : "notEqualMatch";
        }

        throw new IllegalArgumentException("Unsupported filter: " + filterExpr);
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
    private List<CodeGenModel.FilterExpressionV2> convertFilters(List<FilterExpression> filters) {
        List<CodeGenModel.FilterExpressionV2> result = new ArrayList<>();
        for (FilterExpression filter : filters) {
            String matcherClass = getMatcherClassName(filter);
            String left = buildFilterLeft(filter);
            String right = buildFilterRight(filter);
            result.add(CodeGenModel.FilterExpressionV2.builder()
                .expressionObject(matcherClass)
                .left(left)
                .right(right)
                .build());
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
