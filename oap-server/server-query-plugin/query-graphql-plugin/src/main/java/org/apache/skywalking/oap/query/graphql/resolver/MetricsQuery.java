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
import java.util.List;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.query.AggregationQueryService;
import org.apache.skywalking.oap.server.core.query.MetricsMetadataQueryService;
import org.apache.skywalking.oap.server.core.query.MetricsQueryService;
import org.apache.skywalking.oap.server.core.query.TopNRecordsQueryService;
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
    private MetricsQueryService metricsQueryService;
    private AggregationQueryService queryService;
    private TopNRecordsQueryService topNRecordsQueryService;
    private MetricsMetadataQueryService metricsMetadataQueryService;

    public MetricsQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private MetricsMetadataQueryService getMetricsMetadataQueryService() {
        if (metricsMetadataQueryService == null) {
            this.metricsMetadataQueryService = moduleManager.find(CoreModule.NAME)
                                                            .provider()
                                                            .getService(MetricsMetadataQueryService.class);
        }
        return metricsMetadataQueryService;
    }

    private AggregationQueryService getQueryService() {
        if (queryService == null) {
            this.queryService = moduleManager.find(CoreModule.NAME)
                                             .provider()
                                             .getService(AggregationQueryService.class);
        }
        return queryService;
    }

    private TopNRecordsQueryService getTopNRecordsQueryService() {
        if (topNRecordsQueryService == null) {
            this.topNRecordsQueryService = moduleManager.find(CoreModule.NAME)
                                                        .provider()
                                                        .getService(TopNRecordsQueryService.class);
        }
        return topNRecordsQueryService;
    }

    private MetricsQueryService getMetricsQueryService() {
        if (metricsQueryService == null) {
            this.metricsQueryService = moduleManager.find(CoreModule.NAME)
                                                    .provider()
                                                    .getService(MetricsQueryService.class);
        }
        return metricsQueryService;
    }

    /**
     * Metrics definition metadata query. Response the metrics type which determines the suitable query methods.
     */
    public MetricsType typeOfMetrics(String name) throws IOException {
        return getMetricsMetadataQueryService().typeOfMetrics(name);
    }

    /**
     * Read metrics single value in the duration of required metrics
     */
    public int readMetricsValue(MetricsCondition condition, Duration duration) throws IOException {
        return getMetricsQueryService().readMetricsValue(condition, duration);
    }

    /**
     * Read time-series values in the duration of required metrics
     */
    public MetricsValues readMetricsValues(MetricsCondition condition, Duration duration) throws IOException {
        return getMetricsQueryService().readMetricsValues(condition, duration);
    }

    /**
     * Read entity list of required metrics and parent entity type.
     */
    public List<SelectedRecord> sortMetrics(TopNCondition condition, Duration duration) throws IOException {
        return getQueryService().sortMetrics(condition, duration);
    }

    /**
     * Read value in the given time duration, usually as a linear.
     *
     * @param labels the labels you need to query.
     */
    public List<MetricsValues> readLabeledMetricsValues(MetricsCondition condition,
                                                        List<String> labels,
                                                        Duration duration) throws IOException {
        return getMetricsQueryService().readLabeledMetricsValues(condition, labels, duration);
    }

    /**
     * Heatmap is bucket based value statistic result.
     *
     * @return heapmap including the latency distribution {@link HeatMap#getBuckets()} {@link
     * HeatMap.HeatMapColumn#getValues()} follows this rule.
     * <pre>
     *      key = 0, represents [0, 100), value = count of requests in the latency range.
     *      key = 100, represents [100, 200), value = count of requests in the latency range.
     *      ...
     *      key = step * maxNumOfSteps, represents [step * maxNumOfSteps, MAX)
     * </pre>
     */
    public HeatMap readHeatMap(MetricsCondition condition, Duration duration) throws IOException {
        return getMetricsQueryService().readHeatMap(condition, duration);
    }

    /**
     * Read the sampled records.
     */
    public List<SelectedRecord> readSampledRecords(TopNCondition condition, Duration duration) throws IOException {
        return getTopNRecordsQueryService().readSampledRecords(condition, duration);
    }
}
