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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.global.std;

import org.apache.skywalking.apm.collector.analysis.metric.define.graph.MetricGraphIdDefine;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.decorator.*;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.*;
import org.apache.skywalking.apm.collector.configuration.ConfigurationModule;
import org.apache.skywalking.apm.collector.configuration.service.IResponseTimeDistributionConfigService;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.core.graph.*;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.table.global.ResponseTimeDistribution;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class ResponseTimeDistributionSpanListener implements FirstSpanListener {

    private static final Logger logger = LoggerFactory.getLogger(ResponseTimeDistributionSpanListener.class);

    private ResponseTimeDistribution distribution;
    private final IResponseTimeDistributionConfigService configService;

    ResponseTimeDistributionSpanListener(ModuleManager moduleManager) {
        this.distribution = new ResponseTimeDistribution();
        this.configService = moduleManager.find(ConfigurationModule.NAME).getService(IResponseTimeDistributionConfigService.class);
    }

    @Override public boolean containsPoint(Point point) {
        return Point.First.equals(point);
    }

    @Override
    public void parseFirst(SpanDecorator spanDecorator, SegmentCoreInfo segmentCoreInfo) {
        int step = getStep((int)(segmentCoreInfo.getEndTime() - segmentCoreInfo.getStartTime()));
        distribution.setStep(step);
        distribution.setMetricId(String.valueOf(step));
        distribution.setId(segmentCoreInfo.getMinuteTimeBucket() + Const.ID_SPLIT + distribution.getMetricId());

        distribution.setCalls(1);
        distribution.setTimeBucket(segmentCoreInfo.getMinuteTimeBucket());

        if (segmentCoreInfo.isError()) {
            distribution.setErrorCalls(1);
        } else {
            distribution.setSuccessCalls(1);
        }
    }

    @Override public void build() {
        Graph<ResponseTimeDistribution> graph = GraphManager.INSTANCE.findGraph(MetricGraphIdDefine.RESPONSE_TIME_DISTRIBUTION_GRAPH_ID, ResponseTimeDistribution.class);
        graph.start(distribution);
        logger.debug("push to response time distribution aggregation worker, id: {}", distribution.getId());
    }

    int getStep(int duration) {
        int countOfResponseTimeSteps = configService.getResponseTimeStep() * configService.getCountOfResponseTimeSteps();
        int responseTimeStep = configService.getResponseTimeStep();

        if (duration > countOfResponseTimeSteps) {
            return countOfResponseTimeSteps / responseTimeStep;
        } else if (duration <= responseTimeStep) {
            return 0;
        } else {
            return (int)Math.ceil((double)duration / (double)responseTimeStep) - 1;
        }
    }

    public static class Factory implements SpanListenerFactory {

        @GraphComputingMetric(name = "/segment/parse/createSpanListeners/responseTimeDistributionSpanListener")
        @Override public SpanListener create(ModuleManager moduleManager) {
            return new ResponseTimeDistributionSpanListener(moduleManager);
        }
    }
}
