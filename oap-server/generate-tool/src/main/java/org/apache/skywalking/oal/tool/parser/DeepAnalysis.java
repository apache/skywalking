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

package org.apache.skywalking.oal.tool.parser;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.List;
import org.apache.skywalking.oal.tool.util.ClassMethodUtil;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.*;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

public class DeepAnalysis {
    public AnalysisResult analysis(AnalysisResult result) {
        // 1. Set sub package name by source.metrics
        result.setPackageName(result.getSourceName().toLowerCase());

        Class<? extends org.apache.skywalking.oap.server.core.analysis.metrics.Metrics> metricsClass = MetricsHolder.find(result.getAggregationFunctionName());
        String metricsClassSimpleName = metricsClass.getSimpleName();

        result.setMetricsClassName(metricsClassSimpleName);

        // Optional for filter
        List<ConditionExpression> expressions = result.getFilterExpressionsParserResult();
        if (expressions != null && expressions.size() > 0) {
            for (ConditionExpression expression : expressions) {
                FilterExpression filterExpression = new FilterExpression();
                if ("booleanMatch".equals(expression.getExpressionType())) {
                    filterExpression.setExpressionObject("EqualMatch");
                    filterExpression.setLeft("source." + ClassMethodUtil.toIsMethod(expression.getAttribute()) + "()");
                    filterExpression.setRight(expression.getValue());
                    result.addFilterExpressions(filterExpression);
                } else if ("stringMatch".equals(expression.getExpressionType())) {
                    filterExpression.setExpressionObject("EqualMatch");
                    filterExpression.setLeft("source." + ClassMethodUtil.toGetMethod(expression.getAttribute()) + "()");
                    filterExpression.setRight(expression.getValue());
                    result.addFilterExpressions(filterExpression);
                } else if ("greaterMatch".equals(expression.getExpressionType())) {
                    filterExpression.setExpressionObject("GreaterMatch");
                    filterExpression.setLeft("source." + ClassMethodUtil.toGetMethod(expression.getAttribute()) + "()");
                    filterExpression.setRight(expression.getValue());
                    result.addFilterExpressions(filterExpression);
                } else if ("lessMatch".equals(expression.getExpressionType())) {
                    filterExpression.setExpressionObject("LessMatch");
                    filterExpression.setLeft("source." + ClassMethodUtil.toGetMethod(expression.getAttribute()) + "()");
                    filterExpression.setRight(expression.getValue());
                    result.addFilterExpressions(filterExpression);
                } else if ("greaterEqualMatch".equals(expression.getExpressionType())) {
                    filterExpression.setExpressionObject("GreaterEqualMatch");
                    filterExpression.setLeft("source." + ClassMethodUtil.toGetMethod(expression.getAttribute()) + "()");
                    filterExpression.setRight(expression.getValue());
                    result.addFilterExpressions(filterExpression);
                } else if ("lessEqualMatch".equals(expression.getExpressionType())) {
                    filterExpression.setExpressionObject("LessEqualMatch");
                    filterExpression.setLeft("source." + ClassMethodUtil.toGetMethod(expression.getAttribute()) + "()");
                    filterExpression.setRight(expression.getValue());
                    result.addFilterExpressions(filterExpression);
                } else {
                    throw new IllegalArgumentException("filter expression [" + expression.getExpressionType() + "] not found");
                }
            }
        }

        // 3. Find Entrance method of this metrics
        Class c = metricsClass;
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
            Annotation[] parameterAnnotations = parameter.getAnnotations();
            if (parameterAnnotations == null || parameterAnnotations.length == 0) {
                throw new IllegalArgumentException("Entrance method:" + entranceMethod + " doesn't include the annotation.");
            }
            Annotation annotation = parameterAnnotations[0];
            if (annotation instanceof SourceFrom) {
                entryMethod.addArg("source." + ClassMethodUtil.toGetMethod(result.getSourceAttribute()) + "()");
            } else if (annotation instanceof ConstOne) {
                entryMethod.addArg("1");
            } else if (annotation instanceof Expression) {
                entryMethod.addArg("new " + parameter.getType().getName() + "()");
            } else if (annotation instanceof ExpressionArg0) {
                if (result.getFuncConditionExpressions().size() == 1) {
                    ConditionExpression conditionExpression = result.getFuncConditionExpressions().get(0);
                    if ("booleanMatch".equals(conditionExpression.getExpressionType())) {
                        entryMethod.addArg("source." + ClassMethodUtil.toIsMethod(conditionExpression.getAttribute()) + "()");
                    } else if ("stringMatch".equals(conditionExpression.getExpressionType())) {
                        entryMethod.addArg("source." + ClassMethodUtil.toGetMethod(conditionExpression.getAttribute()) + "()");
                    } else {
                        throw new IllegalArgumentException("Entrance method:" + entranceMethod + " argument has @ExpressionArg0, but expression type is not supported");
                    }
                } else {
                    throw new IllegalArgumentException("Entrance method:" + entranceMethod + " argument has @ExpressionArg0, but can't find funcParamExpression.");
                }
            } else if (annotation instanceof ExpressionArg1) {
                if (result.getFuncConditionExpressions().size() == 1) {
                    ConditionExpression conditionExpression = result.getFuncConditionExpressions().get(0);
                    entryMethod.addArg(conditionExpression.getValue());
                } else {
                    throw new IllegalArgumentException("Entrance method:" + entranceMethod + " argument has @ExpressionArg0, but can't find funcParamExpression.");
                }
            } else if (annotation instanceof Arg) {
                entryMethod.addArg(result.getNextFuncArg());
            } else {
                throw new IllegalArgumentException("Entrance method:" + entranceMethod + " doesn't the expected annotation.");
            }
        }

        // 5. Get all column declared in MetricsHolder class.
        c = metricsClass;
        while (!c.equals(Object.class)) {
            for (Field field : c.getDeclaredFields()) {
                Column column = field.getAnnotation(Column.class);
                if (column != null) {
                    result.addPersistentField(field.getName(), column.columnName(), field.getType());
                }
            }
            c = c.getSuperclass();
        }

        // 6. Based on Source, generate default columns
        List<SourceColumn> columns = SourceColumnsFactory.getColumns(result.getSourceName());
        result.setFieldsFromSource(columns);

        result.generateSerializeFields();

        return result;
    }

}
