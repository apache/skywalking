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

package org.apache.skywalking.oal.rt.parser;

import org.apache.skywalking.oal.rt.util.ClassMethodUtil;
import org.apache.skywalking.oal.rt.util.TypeCastUtil;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Arg;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.ConstOne;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.DefaultValue;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Entrance;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.SourceFrom;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import static java.util.Objects.isNull;

public class DeepAnalysis {
    public AnalysisResult analysis(AnalysisResult result) {
        // 1. Set sub package name by source.metrics
        Class<? extends Metrics> metricsClass = MetricsHolder.find(result.getAggregationFuncStmt().getAggregationFunctionName());
        String metricsClassSimpleName = metricsClass.getSimpleName();

        result.setMetricsClassName(metricsClassSimpleName);

        // Optional for filter
        List<ConditionExpression> expressions = result.getFilters().getFilterExpressionsParserResult();
        if (expressions != null && expressions.size() > 0) {
            for (ConditionExpression expression : expressions) {
                final FilterMatchers.MatcherInfo matcherInfo = FilterMatchers.INSTANCE.find(
                    expression.getExpressionType());

                final String getter = matcherInfo.isBooleanType()
                    ? ClassMethodUtil.toIsMethod(expression.getAttributes())
                    : ClassMethodUtil.toGetMethod(expression.getAttributes());

                final Expression filterExpression = new Expression();
                filterExpression.setExpressionObject(matcherInfo.getMatcher().getName());
                filterExpression.setLeft(TypeCastUtil.withCast(expression.getCastType(), "source." + getter));
                filterExpression.setRight(expression.getValue());
                result.getFilters().addFilterExpressions(filterExpression);
            }
        }

        // 3. Find Entrance method of this metrics
        Class<?> c = metricsClass;
        Method entranceMethod = null;
        SearchEntrance:
        while (!c.equals(Object.class)) {
            for (Method method : c.getMethods()) {
                Entrance annotation = method.getAnnotation(Entrance.class);
                if (annotation != null) {
                    entranceMethod = method;
                    break SearchEntrance;
                }
            }
            c = c.getSuperclass();
        }
        if (entranceMethod == null) {
            throw new IllegalArgumentException("Can't find Entrance method in class: " + metricsClass.getName());
        }
        EntryMethod entryMethod = new EntryMethod();
        result.setEntryMethod(entryMethod);
        entryMethod.setMethodName(entranceMethod.getName());

        // 4. Use parameter's annotation of entrance method to generate aggregation entrance.
        for (Parameter parameter : entranceMethod.getParameters()) {
            Class<?> parameterType = parameter.getType();
            Annotation[] parameterAnnotations = parameter.getAnnotations();
            if (parameterAnnotations == null || parameterAnnotations.length == 0) {
                throw new IllegalArgumentException(
                    "Entrance method:" + entranceMethod + " doesn't include the annotation.");
            }
            Annotation annotation = parameterAnnotations[0];
            if (annotation instanceof SourceFrom) {
                entryMethod.addArg(
                    parameterType,
                    TypeCastUtil.withCast(
                        result.getFrom().getSourceCastType(),
                        "source." + ClassMethodUtil.toGetMethod(result.getFrom().getSourceAttribute())
                    )
                );
            } else if (annotation instanceof ConstOne) {
                entryMethod.addArg(parameterType, "1");
            } else if (annotation instanceof org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Expression) {
                if (isNull(result.getAggregationFuncStmt().getFuncConditionExpressions())
                    || result.getAggregationFuncStmt().getFuncConditionExpressions().isEmpty()) {
                    throw new IllegalArgumentException(
                        "Entrance method:" + entranceMethod + " argument can't find funcParamExpression.");
                } else {
                    ConditionExpression expression = result.getAggregationFuncStmt().getNextFuncConditionExpression();
                    final FilterMatchers.MatcherInfo matcherInfo = FilterMatchers.INSTANCE.find(
                        expression.getExpressionType());

                    final String getter = matcherInfo.isBooleanType()
                        ? ClassMethodUtil.toIsMethod(expression.getAttributes())
                        : ClassMethodUtil.toGetMethod(expression.getAttributes());

                    final Expression argExpression = new Expression();
                    argExpression.setRight(expression.getValue());
                    argExpression.setExpressionObject(matcherInfo.getMatcher().getName());
                    argExpression.setLeft(TypeCastUtil.withCast(expression.getCastType(), "source." + getter));

                    entryMethod.addArg(argExpression);
                }
            } else if (annotation instanceof Arg) {
                entryMethod.addArg(parameterType, result.getAggregationFuncStmt().getNextFuncArg());
            } else if (annotation instanceof DefaultValue) {
                if (result.getAggregationFuncStmt().hasNextArg()) {
                    entryMethod.addArg(parameterType, result.getAggregationFuncStmt().getNextFuncArg());
                } else {
                    entryMethod.addArg(parameterType, ((DefaultValue) annotation).value());
                }
            } else {
                throw new IllegalArgumentException(
                    "Entrance method:" + entranceMethod + " doesn't the expected annotation.");
            }
        }

        // 5. Get all column declared in MetricsHolder class.
        c = metricsClass;
        while (!c.equals(Object.class)) {
            for (Field field : c.getDeclaredFields()) {
                Column column = field.getAnnotation(Column.class);
                if (column != null) {
                    result.addPersistentField(
                        field.getName(),
                        column.name(),
                        field.getType());
                }
            }
            c = c.getSuperclass();
        }

        // 6. Based on Source, generate default columns
        List<SourceColumn> columns = SourceColumnsFactory.getColumns(result.getFrom().getSourceName());
        result.setFieldsFromSource(columns);

        result.generateSerializeFields();

        return result;
    }

}
