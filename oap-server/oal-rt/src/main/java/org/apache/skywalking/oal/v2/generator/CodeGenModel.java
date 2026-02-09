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

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import org.apache.skywalking.oal.v2.model.FilterExpression;
import org.apache.skywalking.oal.v2.model.MetricDefinition;

/**
 * V2 code generation model.
 *
 * This model contains all information needed by Freemarker templates to generate
 * metrics classes, builders, and dispatchers.
 *
 * Unlike V1's AnalysisResult, this model is built directly from V2's MetricDefinition
 * without any V1 dependency.
 */
@Getter
@Builder
public class CodeGenModel {

    /**
     * Original V2 metric definition.
     */
    private MetricDefinition metricDefinition;

    /**
     * Variable name (snake_case from OAL script).
     * Example: "service_resp_time"
     */
    private String varName;

    /**
     * Generated metrics class name (PascalCase).
     * Example: "ServiceRespTime"
     */
    private String metricsName;

    /**
     * Package for generated metrics class.
     * Example: "org.apache.skywalking.oap.server.core.source.oal.rt.metrics."
     */
    private String metricsClassPackage;

    /**
     * Package for source classes.
     * Example: "org.apache.skywalking.oap.server.core.source."
     */
    private String sourcePackage;

    /**
     * Storage table name.
     * Example: "service_resp_time"
     */
    private String tableName;

    /**
     * Source class name.
     * Example: "Service"
     */
    private String sourceName;

    /**
     * Source scope ID (from source metadata).
     */
    private int sourceScopeId;

    /**
     * V1-compatible "from" structure for templates.
     * Templates reference ${from.sourceScopeId}.
     */
    private FromStmtV2 from;

    /**
     * Aggregation function name.
     * Example: "longAvg"
     */
    private String functionName;

    /**
     * Metrics function class name.
     * Example: "LongAvgMetrics"
     */
    private String metricsClassName;

    /**
     * Filter expressions from OAL script.
     */
    @Builder.Default
    private List<FilterExpression> filters = new ArrayList<>();

    /**
     * Fields extracted from source (for ID, persistence).
     */
    @Builder.Default
    private List<SourceFieldV2> fieldsFromSource = new ArrayList<>();

    /**
     * Fields from metrics function (persistent columns).
     */
    @Builder.Default
    private List<DataFieldV2> persistentFields = new ArrayList<>();

    /**
     * Serialization fields (combination of source + persistent).
     */
    private SerializeFieldsV2 serializeFields;

    /**
     * Entrance method information.
     */
    private EntranceMethodV2 entranceMethod;

    /**
     * Optional source decorator.
     */
    private String sourceDecorator;

    /**
     * Source field for code generation templates.
     */
    @Getter
    @Builder
    public static class SourceFieldV2 {
        private String fieldName;
        private String columnName;
        private String typeName;
        private Class<?> type;
        private boolean isID;
        private boolean isShardingKey;
        private int shardingKeyIdx;
        private int length;
        private boolean attribute;

        public String getFieldGetter() {
            return "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        }

        public String getFieldSetter() {
            return "set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        }
    }

    /**
     * Data field (persistent column from metrics function).
     * Compatible with V1's DataColumn structure.
     */
    @Getter
    @Builder
    public static class DataFieldV2 {
        private String fieldName;
        private String columnName;
        private Class<?> type;
        private String typeName;  // V1 compatibility: string representation of type

        public String getFieldGetter() {
            return "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        }

        public String getFieldSetter() {
            return "set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        }

        public String getFieldType() {
            return typeName != null ? typeName : (type != null ? type.getName() : "");
        }
    }

    /**
     * Entrance method information for dispatcher.
     */
    @Getter
    @Builder
    public static class EntranceMethodV2 {
        private String methodName;
        @Builder.Default
        private List<String> argsExpressions = new ArrayList<>();
        @Builder.Default
        private List<Integer> argTypes = new ArrayList<>();
    }

    /**
     * Serialization fields model for templates.
     * Compatible with V1's PersistenceColumns structure.
     */
    @Getter
    @Builder
    public static class SerializeFieldsV2 {
        @Builder.Default
        private List<PersistenceFieldV2> intFields = new ArrayList<>();
        @Builder.Default
        private List<PersistenceFieldV2> doubleFields = new ArrayList<>();
        @Builder.Default
        private List<PersistenceFieldV2> stringFields = new ArrayList<>();
        @Builder.Default
        private List<PersistenceFieldV2> longFields = new ArrayList<>();
        @Builder.Default
        private List<PersistenceFieldV2> objectFields = new ArrayList<>();

        public void addIntField(String fieldName) {
            intFields.add(new PersistenceFieldV2(fieldName, "int"));
        }

        public void addDoubleField(String fieldName) {
            doubleFields.add(new PersistenceFieldV2(fieldName, "double"));
        }

        public void addStringField(String fieldName) {
            stringFields.add(new PersistenceFieldV2(fieldName, "String"));
        }

        public void addLongField(String fieldName) {
            longFields.add(new PersistenceFieldV2(fieldName, "long"));
        }

        public void addObjectField(String fieldName, String className) {
            objectFields.add(new PersistenceFieldV2(fieldName, className));
        }
    }

    /**
     * Persistence field compatible with V1's PersistenceField.
     * Provides getter/setter method names for templates.
     */
    @Getter
    public static class PersistenceFieldV2 {
        private final String fieldName;
        private final String getter;
        private final String setter;
        private final String fieldType;

        public PersistenceFieldV2(String fieldName, String fieldType) {
            this.fieldName = fieldName;
            this.fieldType = fieldType;
            this.getter = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
            this.setter = "set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        }
    }


    /**
     * Filter expression model for templates.
     */
    @Getter
    @Builder
    public static class FilterExpressionV2 {
        private String expressionObject;
        private String left;
        private String right;
    }

    /**
     * V1-compatible "from" statement for templates.
     */
    @Getter
    @Builder
    public static class FromStmtV2 {
        private int sourceScopeId;
    }
}
