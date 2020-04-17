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

package org.apache.skywalking.oap.query.graphql.resolver;

import com.coxautodev.graphql.tools.GraphQLQueryResolver;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.query.AggregationQueryService;
import org.apache.skywalking.oap.server.core.query.MetricQueryService;
import org.apache.skywalking.oap.server.core.query.enumeration.MetricsType;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.HeatMap;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * Metrics v2 query protocol implementation.
 *
 * @since 8.0.0
 */
public class MetricsQuery implements GraphQLQueryResolver {
    private final ModuleManager moduleManager;
    private MetricQueryService metricQueryService;
    private AggregationQueryService queryService;

    public MetricsQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private AggregationQueryService getQueryService() {
        if (queryService == null) {
            this.queryService = moduleManager.find(CoreModule.NAME)
                                             .provider()
                                             .getService(AggregationQueryService.class);
        }
        return queryService;
    }

    /**
     * Metrics definition metadata query. Response the metrics type which determines the suitable query methods.
     *
     * @since 8.0.0
     */
    public MetricsType typeOfMetrics(String name) throws IOException {
        return MetricsType.UNKNOWN;
    }

    /**
     * Read metrics single value in the duration of required metrics
     *
     * @since 8.0.0
     */
    public int readMetricsValue(MetricsCondition metrics, Duration duration) throws IOException {
        return 0;
    }

    /**
     * Read time-series values in the duration of required metrics
     *
     * @since 8.0.0
     */
    public List<MetricsValues> readMetricsValues(MetricsCondition metrics, Duration duration) throws IOException {
        return Collections.emptyList();
    }

    /**
     * Read entity list of required metrics and parent entity type.
     *
     * @since 8.0.0
     */
    public List<SelectedRecord> sortMetrics(TopNCondition metrics, Duration duration) throws IOException {
        return getQueryService().sortMetrics(metrics, duration);
    }

    /**
     * Read value in the given time duration, usually as a linear.
     *
     * @param labels the labels you need to query.
     * @since 8.0.0
     */
    public List<MetricsValues> readLabeledMetricsValues(MetricsCondition metrics,
                                                        List<String> labels,
                                                        Duration duration) throws IOException {
        return Collections.emptyList();
    }

    /**
     * Heatmap is bucket based value statistic result.
     *
     * @since 8.0.0
     */
    public HeatMap readHeatMap(MetricsCondition metrics, Duration duration) throws IOException {
        return new HeatMap();
    }

    /**
     * Read the sampled records.
     *
     * @since 8.0.0
     */
    public List<SelectedRecord> readSampledRecords(TopNCondition metrics, Duration duration) throws IOException {
        return Collections.emptyList();
    }
}
