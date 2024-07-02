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

package org.apache.skywalking.oap.server.core.query;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.type.HeatMap;
import org.apache.skywalking.oap.server.core.query.type.KVInt;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.query.type.NullableValue;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;

import static org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext.TRACE_CONTEXT;

@Slf4j
public class MetricsQueryService implements Service {
    private final ModuleManager moduleManager;
    private IMetricsQueryDAO metricQueryDAO;

    public MetricsQueryService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IMetricsQueryDAO getMetricQueryDAO() {
        if (metricQueryDAO == null) {
            metricQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(IMetricsQueryDAO.class);
        }
        return metricQueryDAO;
    }

    /**
     * Read metrics average value in the duration of required metrics
     */
    public NullableValue readMetricsValue(MetricsCondition condition, Duration duration) throws IOException {
        long defaultValue = ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName());
        if (!condition.senseScope() || !condition.getEntity().isValid()) {
            return new NullableValue(defaultValue, true);
        }
        MetricsValues metricsValues = readMetricsValues(condition, duration);
        if (!metricsValues.getValues().getValues().isEmpty()) {
           OptionalDouble avgValue = metricsValues.getValues().getValues().stream().filter(v -> !v.isEmptyValue()).mapToLong(
               KVInt::getValue).average();
           if (avgValue.isPresent()) {
               return new NullableValue((long) avgValue.getAsDouble(), false);
           }
        }

        return new NullableValue(defaultValue, true);
    }

    private MetricsValues invokeReadMetricsValues(MetricsCondition condition, Duration duration) throws IOException {
        if (!condition.senseScope() || !condition.getEntity().isValid()) {
            return new MetricsValues();
        }
        return getMetricQueryDAO().readMetricsValuesDebuggable(
            condition, ValueColumnMetadata.INSTANCE.getValueCName(condition.getName()), duration);
    }

    /**
     * Read time-series values in the duration of required metrics
     */
    public MetricsValues readMetricsValues(MetricsCondition condition, Duration duration) throws IOException {
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Service: readMetricsValues");
                span.setMsg("MetricsCondition: " + condition + ", Duration: " + duration);
            }
            return invokeReadMetricsValues(condition, duration);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    private List<MetricsValues> invokeReadLabeledMetricsValues(MetricsCondition condition,
                                                        List<KeyValue> labels,
                                                        Duration duration) throws IOException {
        if (!condition.senseScope() || !condition.getEntity().isValid()) {
            return Collections.emptyList();
        }
        return getMetricQueryDAO().readLabeledMetricsValuesDebuggable(
            condition, ValueColumnMetadata.INSTANCE.getValueCName(condition.getName()), labels, duration);
    }

    /**
     * Read value in the given time duration, usually as a linear.
     *
     * @param labels the labels you need to query.
     */
    public List<MetricsValues> readLabeledMetricsValues(MetricsCondition condition,
                                                         List<KeyValue> labels,
                                                         Duration duration) throws IOException {
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Service: readLabeledMetricsValues");
                span.setMsg("MetricsCondition: " + condition + ", Labels: " + labels + ", Duration: " + duration);
            }
            return invokeReadLabeledMetricsValues(condition, labels, duration);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    public List<MetricsValues> readLabeledMetricsValuesWithoutEntity(String metricsName,
                                         List<KeyValue> labels,
                                         Duration duration) throws IOException {
        return getMetricQueryDAO().readLabeledMetricsValuesWithoutEntity(metricsName, ValueColumnMetadata.INSTANCE.getValueCName(metricsName), labels, duration);
    }

    /**
     * Heatmap is bucket based value statistic result.
     */
    public HeatMap readHeatMap(MetricsCondition condition, Duration duration) throws IOException {
        if (!condition.senseScope() || !condition.getEntity().isValid()) {
            return new HeatMap();
        }
        return getMetricQueryDAO().readHeatMap(
            condition, ValueColumnMetadata.INSTANCE.getValueCName(condition.getName()), duration);
    }
}
