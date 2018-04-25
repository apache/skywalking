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
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.decorator.SpanDecorator;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.EntrySpanListener;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.ExitSpanListener;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.FirstSpanListener;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.LocalSpanListener;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.SpanListener;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.SpanListenerFactory;
import org.apache.skywalking.apm.collector.configuration.ConfigurationModule;
import org.apache.skywalking.apm.collector.configuration.service.IResponseTimeDistributionConfigService;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.core.graph.Graph;
import org.apache.skywalking.apm.collector.core.graph.GraphManager;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.table.global.ResponseTimeDistribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ResponseTimeDistributionSpanListener implements FirstSpanListener, EntrySpanListener, ExitSpanListener, LocalSpanListener {

    private static final Logger logger = LoggerFactory.getLogger(ResponseTimeDistributionSpanListener.class);

    private long timeBucket;
    private boolean isError = false;
    private int entrySpanDuration = 0;
    private int firstSpanDuration = 0;
    private final IResponseTimeDistributionConfigService configService;

    ResponseTimeDistributionSpanListener(ModuleManager moduleManager) {
        this.configService = moduleManager.find(ConfigurationModule.NAME).getService(IResponseTimeDistributionConfigService.class);
    }

    @Override public boolean containsPoint(Point point) {
        return Point.First.equals(point) || Point.Entry.equals(point) || Point.Exit.equals(point) || Point.Local.equals(point);
    }

    @Override public void parseEntry(SpanDecorator spanDecorator, int applicationId, int instanceId, String segmentId) {
        isError = isError || spanDecorator.getIsError();
        entrySpanDuration = (int)(spanDecorator.getEndTime() - spanDecorator.getStartTime());
    }

    @Override
    public void parseFirst(SpanDecorator spanDecorator, int applicationId, int instanceId,
        String segmentId) {
        isError = isError || spanDecorator.getIsError();

        if (spanDecorator.getStartTimeMinuteTimeBucket() == 0) {
            long startTimeMinuteTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanDecorator.getStartTime());
            spanDecorator.setStartTimeMinuteTimeBucket(startTimeMinuteTimeBucket);
        }
        timeBucket = spanDecorator.getStartTimeMinuteTimeBucket();

        firstSpanDuration = (int)(spanDecorator.getEndTime() - spanDecorator.getStartTime());
    }

    @Override public void parseExit(SpanDecorator spanDecorator, int applicationId, int instanceId, String segmentId) {
        isError = isError || spanDecorator.getIsError();
    }

    @Override public void parseLocal(SpanDecorator spanDecorator, int applicationId, int instanceId, String segmentId) {
        isError = isError || spanDecorator.getIsError();
    }

    @Override public void build() {
        int step = getStep();

        ResponseTimeDistribution distribution = new ResponseTimeDistribution();
        distribution.setMetricId(String.valueOf(step));
        distribution.setId(timeBucket + Const.ID_SPLIT + distribution.getMetricId());
        distribution.setStep(step);
        distribution.setCalls(1);
        distribution.setTimeBucket(timeBucket);

        if (isError) {
            distribution.setErrorCalls(1);
        } else {
            distribution.setSuccessCalls(1);
        }

        Graph<ResponseTimeDistribution> graph = GraphManager.INSTANCE.findGraph(MetricGraphIdDefine.RESPONSE_TIME_DISTRIBUTION_GRAPH_ID, ResponseTimeDistribution.class);
        graph.start(distribution);
        logger.debug("push to response time distribution aggregation worker, id: {}", distribution.getId());
    }

    int getStep() {
        int responseTimeMaxStep = configService.getResponseTimeStep() * configService.getResponseTimeMaxStep();
        int responseTimeStep = configService.getResponseTimeStep();

        int duration;
        if (entrySpanDuration == 0) {
            duration = firstSpanDuration;
        } else {
            duration = entrySpanDuration;
        }

        if (duration > responseTimeMaxStep) {
            return responseTimeMaxStep / responseTimeStep;
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
