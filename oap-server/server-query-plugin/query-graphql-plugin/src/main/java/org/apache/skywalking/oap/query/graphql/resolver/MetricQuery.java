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
import java.text.ParseException;
import java.util.List;
import org.apache.skywalking.oap.query.graphql.type.BatchMetricConditions;
import org.apache.skywalking.oap.query.graphql.type.Duration;
import org.apache.skywalking.oap.query.graphql.type.MetricCondition;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.MetricQueryService;
import org.apache.skywalking.oap.server.core.query.StepToDownsampling;
import org.apache.skywalking.oap.server.core.query.entity.IntValues;
import org.apache.skywalking.oap.server.core.query.entity.Thermodynamic;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * @author peng-yongsheng
 */
public class MetricQuery implements GraphQLQueryResolver {

    private final ModuleManager moduleManager;
    private MetricQueryService metricQueryService;

    public MetricQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private MetricQueryService getMetricQueryService() {
        if (metricQueryService == null) {
            this.metricQueryService = moduleManager.find(CoreModule.NAME).provider().getService(MetricQueryService.class);
        }
        return metricQueryService;
    }

    public IntValues getValues(final BatchMetricConditions metrics, final Duration duration) throws IOException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        return getMetricQueryService().getValues(metrics.getName(), metrics.getIds(), StepToDownsampling.transform(duration.getStep()), startTimeBucket, endTimeBucket);
    }

    public IntValues getLinearIntValues(final MetricCondition metrics,
        final Duration duration) throws IOException, ParseException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        return getMetricQueryService().getLinearIntValues(metrics.getName(), metrics.getId(), StepToDownsampling.transform(duration.getStep()), startTimeBucket, endTimeBucket);
    }

    public List<IntValues> getMultipleLinearIntValues(final MetricCondition metrics, final int numOfLinear,
        final Duration duration) throws IOException, ParseException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        return getMetricQueryService().getMultipleLinearIntValues(metrics.getName(), metrics.getId(), numOfLinear, StepToDownsampling.transform(duration.getStep()), startTimeBucket, endTimeBucket);
    }

    public List<IntValues> getSubsetOfMultipleLinearIntValues(final MetricCondition metrics,
        final List<Integer> linearIndex, final Duration duration) throws IOException, ParseException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        return getMetricQueryService().getSubsetOfMultipleLinearIntValues(metrics.getName(), metrics.getId(), linearIndex, StepToDownsampling.transform(duration.getStep()), startTimeBucket, endTimeBucket);
    }

    public Thermodynamic getThermodynamic(final MetricCondition metrics,
        final Duration duration) throws IOException, ParseException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        return getMetricQueryService().getThermodynamic(metrics.getName(), metrics.getId(), StepToDownsampling.transform(duration.getStep()), startTimeBucket, endTimeBucket);
    }
}
