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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;

@Getter
@Setter
public class AnalysisResult {
    private String varName;

    private String metricsName;

    private String metricsClassPackage;

    private String tableName;

    private String packageName;

    private String sourcePackage;

    private String sourceName;

    private int sourceScopeId;

    private List<String> sourceAttribute = new ArrayList<>();

    private String aggregationFunctionName;

    private String metricsClassName;

    private EntryMethod entryMethod;

    private List<Expression> filterExpressions;

    private List<ConditionExpression> filterExpressionsParserResult;

    private List<ConditionExpression> funcConditionExpressions;

    private int funcConditionExpressionGetIdx = 0;

    private List<Argument> funcArgs;

    private int argGetIdx = 0;

    private List<DataColumn> persistentFields;

    private List<SourceColumn> fieldsFromSource;

    private PersistenceColumns serializeFields;

    public void addPersistentField(String fieldName, String columnName, Class<?> type) {
        if (persistentFields == null) {
            persistentFields = new LinkedList<>();
        }
        DataColumn dataColumn = new DataColumn(fieldName, columnName, type);
        persistentFields.add(dataColumn);
    }

    public void addFuncConditionExpression(ConditionExpression conditionExpression) {
        if (funcConditionExpressions == null) {
            funcConditionExpressions = new LinkedList<>();
        }
        funcConditionExpressions.add(conditionExpression);
    }

    public ConditionExpression getNextFuncConditionExpression() {
        return funcConditionExpressions.get(funcConditionExpressionGetIdx++);
    }

    public void addFilterExpressions(Expression filterExpression) {
        if (filterExpressions == null) {
            filterExpressions = new LinkedList<>();
        }
        filterExpressions.add(filterExpression);
    }

    public void addFilterExpressionsParserResult(ConditionExpression conditionExpression) {
        if (filterExpressionsParserResult == null) {
            filterExpressionsParserResult = new LinkedList<>();
        }
        filterExpressionsParserResult.add(conditionExpression);
    }

    public void addFuncArg(Argument argument) {
        if (funcArgs == null) {
            funcArgs = new LinkedList<>();
        }
        funcArgs.add(argument);
    }

    public Argument getNextFuncArg() {
        return funcArgs.get(argGetIdx++);
    }

    public void generateSerializeFields() {
        serializeFields = new PersistenceColumns();
        for (SourceColumn sourceColumn : fieldsFromSource) {
            String type = sourceColumn.getType().getSimpleName();
            switch (type) {
                case "int":
                    serializeFields.addIntField(sourceColumn.getFieldName());
                    break;
                case "double":
                    serializeFields.addDoubleField(sourceColumn.getFieldName());
                    break;
                case "String":
                    serializeFields.addStringField(sourceColumn.getFieldName());
                    break;
                case "long":
                    serializeFields.addLongField(sourceColumn.getFieldName());
                    break;
                default:
                    throw new IllegalStateException(
                        "Unexpected field type [" + type + "] of source sourceColumn [" + sourceColumn
                            .getFieldName() + "]");
            }
        }

        for (DataColumn column : persistentFields) {
            final Class<?> columnType = column.getType();

            if (columnType.equals(int.class)) {
                serializeFields.addIntField(column.getFieldName());
            } else if (columnType.equals(double.class)) {
                serializeFields.addDoubleField(column.getFieldName());
            } else if (columnType.equals(String.class)) {
                serializeFields.addStringField(column.getFieldName());
            } else if (columnType.equals(long.class)) {
                serializeFields.addLongField(column.getFieldName());
            } else if (StorageDataComplexObject.class.isAssignableFrom(columnType)) {
                serializeFields.addObjectField(column.getFieldName(), columnType.getName());
            } else {
                throw new IllegalStateException(
                    "Unexpected field type [" + columnType.getSimpleName() + "] of persistence column [" + column
                        .getFieldName() + "]");
            }
        }
    }
}
