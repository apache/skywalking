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
import org.apache.skywalking.oal.v2.model.MetricDefinition;

/**
 * Code generation model for OAL metrics.
 *
 * <p>This model contains all information needed by FreeMarker templates to generate
 * metrics classes, builders, and dispatchers at runtime.
 *
 * <p>Built from {@link MetricDefinition} via {@link MetricDefinitionEnricher}, this model
 * provides template-ready data structures with precomputed method names, type information,
 * and serialization metadata.
 *
 * <p>Example OAL input:
 * <pre>
 * service_resp_time = from(Service.latency).longAvg();
 * </pre>
 *
 * <p>Produces a CodeGenModel with:
 * <ul>
 *   <li>varName = "service_resp_time"</li>
 *   <li>metricsName = "ServiceRespTime"</li>
 *   <li>tableName = "service_resp_time"</li>
 *   <li>sourceName = "Service"</li>
 *   <li>functionName = "longAvg"</li>
 *   <li>metricsClassName = "LongAvgMetrics"</li>
 * </ul>
 *
 * @see MetricDefinitionEnricher
 * @see OALClassGeneratorV2
 */
@Getter
@Builder
public class CodeGenModel {

    /**
     * Original parsed metric definition from OAL script.
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
     * Source statement for templates.
     * Templates reference {@code ${from.sourceScopeId}}.
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
     * Filter expressions wrapper for templates.
     * Templates access via {@code ${filters.filterExpressions}}.
     */
    private FiltersV2 filters;

    /**
     * Filter expressions converted for template use.
     * Each contains expressionObject, left, right for filter checks.
     */
    @Builder.Default
    private List<FilterExpressionV2> filterExpressions = new ArrayList<>();

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
     * Entrance method information for dispatcher code generation.
     * Contains method name, argument expressions, and argument types.
     */
    private EntranceMethodV2 entranceMethod;

    /**
     * Optional source decorator.
     */
    private String sourceDecorator;

    /**
     * Alias getter for entranceMethod.
     * Templates may reference {@code ${entryMethod.methodName}}.
     *
     * @return the entrance method information
     */
    public EntranceMethodV2 getEntryMethod() {
        return entranceMethod;
    }

    /**
     * Source field extracted from a source class (e.g., Service, Endpoint).
     *
     * <p>These fields become columns in the generated metrics class and are used
     * for entity identification (ID fields) and storage partitioning (sharding keys).
     *
     * <p>Example: For source "Service", fields include "entityId" (ID field)
     * and "name" (regular field).
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

        /**
         * Returns getter method name for this field.
         * Example: fieldName="entityId" returns "getEntityId"
         *
         * @return getter method name
         */
        public String getFieldGetter() {
            return "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        }

        /**
         * Returns setter method name for this field.
         * Example: fieldName="entityId" returns "setEntityId"
         *
         * @return setter method name
         */
        public String getFieldSetter() {
            return "set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        }
    }

    /**
     * Data field representing a persistent column from the metrics function class.
     *
     * <p>These fields come from {@code @Column} annotations on the parent metrics class
     * (e.g., LongAvgMetrics has "summation", "count", "value" fields).
     *
     * <p>Used for serialization/deserialization and storage operations.
     */
    @Getter
    @Builder
    public static class DataFieldV2 {
        private String fieldName;
        private String columnName;
        private Class<?> type;
        private String typeName;

        /**
         * Returns getter method name for this field.
         * Example: fieldName="summation" returns "getSummation"
         *
         * @return getter method name
         */
        public String getFieldGetter() {
            return "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        }

        /**
         * Returns setter method name for this field.
         * Example: fieldName="summation" returns "setSummation"
         *
         * @return setter method name
         */
        public String getFieldSetter() {
            return "set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        }

        /**
         * Returns fully qualified type name for this field.
         *
         * @return type name (e.g., "long", "java.lang.String")
         */
        public String getFieldType() {
            return typeName != null ? typeName : (type != null ? type.getName() : "");
        }
    }

    /**
     * Entrance method information for dispatcher code generation.
     *
     * <p>The entrance method is the method annotated with {@code @Entrance} on the
     * metrics function class. The dispatcher calls this method to process source data.
     *
     * <p>Argument types:
     * <ul>
     *   <li>1 = LITERAL_TYPE - literal value (e.g., "1", "100")</li>
     *   <li>2 = ATTRIBUTE_EXP_TYPE - source attribute expression (e.g., "source.getLatency()")</li>
     *   <li>3 = EXPRESSION_TYPE - filter expression object with matcher</li>
     * </ul>
     */
    @Getter
    @Builder
    public static class EntranceMethodV2 {
        private String methodName;
        @Builder.Default
        private List<Object> argsExpressions = new ArrayList<>();
        @Builder.Default
        private List<Integer> argTypes = new ArrayList<>();
    }

    /**
     * Serialization fields grouped by type for template-based code generation.
     *
     * <p>Used by serialize/deserialize templates to generate type-specific
     * serialization code. Fields are grouped by primitive type to enable
     * efficient batch processing in generated code.
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
     * Persistence field with precomputed getter/setter method names.
     *
     * <p>Provides the field name, type, and accessor method names needed
     * by serialization templates to generate accessor code.
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
     * Filter expression for dispatcher code generation.
     *
     * <p>Represents a filter condition from OAL like {@code filter(status == 200)}.
     * Contains the matcher class and operand expressions.
     *
     * <p>Example for {@code filter(status == 200)}:
     * <ul>
     *   <li>expressionObject = "org.apache.skywalking.oap...EqualMatch"</li>
     *   <li>left = "source.getStatus()"</li>
     *   <li>right = "200"</li>
     * </ul>
     */
    @Getter
    @Builder
    public static class FilterExpressionV2 {
        /** Fully qualified matcher class name. */
        private String expressionObject;
        /** Left operand expression (usually source getter). */
        private String left;
        /** Right operand expression (literal or source getter). */
        private String right;
    }

    /**
     * Source (from) statement information.
     *
     * <p>Contains the source class name and scope ID from the OAL "from" clause.
     * Templates access via {@code ${from.sourceName}} and {@code ${from.sourceScopeId}}.
     */
    @Getter
    @Builder
    public static class FromStmtV2 {
        /** Source class name (e.g., "Service", "Endpoint"). */
        private String sourceName;
        /** Scope ID for this source type. */
        private int sourceScopeId;
    }

    /**
     * Filter expressions wrapper for template access.
     *
     * <p>Wraps the list of filter expressions for template iteration.
     * Templates access via {@code ${filters.filterExpressions}}.
     */
    @Getter
    @Builder
    public static class FiltersV2 {
        @Builder.Default
        private List<FilterExpressionV2> filterExpressions = new ArrayList<>();
    }
}
