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

package org.apache.skywalking.oap.server.core.storage.annotation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.query.sql.Function;

/**
 * ValueColumnMetadata holds the metadata for column values of metrics. The metadata of ValueColumn is declared through
 * {@link Column} annotation.
 */
public enum ValueColumnMetadata {
    INSTANCE;

    private Map<String, ValueColumn> mapping = new HashMap<>();

    /**
     * Register the new metadata for the given model name.
     */
    public void putIfAbsent(String modelName,
                            String valueCName,
                            Column.ValueDataType dataType,
                            Function function,
                            int defaultValue,
                            int scopeId) {
        mapping.putIfAbsent(modelName, new ValueColumn(valueCName, dataType, function, defaultValue, scopeId));
    }

    /**
     * Fetch the value column name of the given metrics name.
     */
    public String getValueCName(String metricsName) {
        return findColumn(metricsName).valueCName;
    }

    /**
     * Fetch the function for the value column of the given metrics name.
     */
    public Function getValueFunction(String metricsName) {
        return findColumn(metricsName).function;
    }

    public int getDefaultValue(String metricsName) {
        return findColumn(metricsName).defaultValue;
    }

    /**
     * @return metric metadata if found
     */
    public Optional<ValueColumn> readValueColumnDefinition(String metricsName) {
        return Optional.ofNullable(mapping.get(metricsName));
    }

    /**
     * @return all metrics metadata.
     */
    public Map<String, ValueColumn> getAllMetadata() {
        return mapping;
    }

    private ValueColumn findColumn(String metricsName) {
        ValueColumn column = mapping.get(metricsName);
        if (column == null) {
            throw new RuntimeException("Metrics:" + metricsName + " doesn't have value column definition");
        }
        return column;
    }

    @Getter
    @RequiredArgsConstructor
    public class ValueColumn {
        private final String valueCName;
        private final Column.ValueDataType dataType;
        private final Function function;
        private final int defaultValue;
        private final int scopeId;
    }
}
