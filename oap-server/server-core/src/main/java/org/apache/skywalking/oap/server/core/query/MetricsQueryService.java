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
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.type.HeatMap;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;

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
     * Read metrics single value in the duration of required metrics
     */
    public long readMetricsValue(MetricsCondition condition, Duration duration) throws IOException {
        return getMetricQueryDAO().readMetricsValue(
            condition, ValueColumnMetadata.INSTANCE.getValueCName(condition.getName()), duration);
    }

    /**
     * Read time-series values in the duration of required metrics
     */
    public MetricsValues readMetricsValues(MetricsCondition condition, Duration duration) throws IOException {
        return getMetricQueryDAO().readMetricsValues(
            condition, ValueColumnMetadata.INSTANCE.getValueCName(condition.getName()), duration);
    }

    /**
     * Read value in the given time duration, usually as a linear.
     *
     * @param labels the labels you need to query.
     */
    public List<MetricsValues> readLabeledMetricsValues(MetricsCondition condition,
                                                        List<String> labels,
                                                        Duration duration) throws IOException {
        return getMetricQueryDAO().readLabeledMetricsValues(
            condition, ValueColumnMetadata.INSTANCE.getValueCName(condition.getName()), labels, duration);
    }

    /**
     * Heatmap is bucket based value statistic result.
     */
    public HeatMap readHeatMap(MetricsCondition condition, Duration duration) throws IOException {
        return getMetricQueryDAO().readHeatMap(
            condition, ValueColumnMetadata.INSTANCE.getValueCName(condition.getName()), duration);
    }
}
