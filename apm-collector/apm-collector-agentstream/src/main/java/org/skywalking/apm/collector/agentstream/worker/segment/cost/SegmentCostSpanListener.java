/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.agentstream.worker.segment.cost;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.agentstream.worker.segment.EntrySpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.ExitSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.FirstSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.LocalSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.standardization.SpanDecorator;
import org.skywalking.apm.collector.cache.ServiceNameCache;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.define.segment.SegmentCostDataDefine;
import org.skywalking.apm.collector.stream.StreamModuleContext;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
import org.skywalking.apm.collector.stream.worker.WorkerInvokeException;
import org.skywalking.apm.collector.stream.worker.WorkerNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class SegmentCostSpanListener implements EntrySpanListener, ExitSpanListener, LocalSpanListener, FirstSpanListener {

    private final Logger logger = LoggerFactory.getLogger(SegmentCostSpanListener.class);

    private List<SegmentCostDataDefine.SegmentCost> segmentCosts = new ArrayList<>();
    private boolean isError = false;
    private long timeBucket;

    @Override
    public void parseFirst(SpanDecorator spanDecorator, int applicationId, int applicationInstanceId,
        String segmentId) {
        timeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanDecorator.getStartTime());

        SegmentCostDataDefine.SegmentCost segmentCost = new SegmentCostDataDefine.SegmentCost();
        segmentCost.setSegmentId(segmentId);
        segmentCost.setApplicationId(applicationId);
        segmentCost.setCost(spanDecorator.getEndTime() - spanDecorator.getStartTime());
        segmentCost.setStartTime(spanDecorator.getStartTime());
        segmentCost.setEndTime(spanDecorator.getEndTime());
        segmentCost.setId(segmentId);
        if (spanDecorator.getOperationNameId() == 0) {
            segmentCost.setServiceName(spanDecorator.getOperationName());
        } else {
            segmentCost.setServiceName(ServiceNameCache.getSplitServiceName(ServiceNameCache.get(spanDecorator.getOperationNameId())));
        }

        segmentCosts.add(segmentCost);
        isError = isError || spanDecorator.getIsError();
    }

    @Override
    public void parseEntry(SpanDecorator spanDecorator, int applicationId, int applicationInstanceId,
        String segmentId) {
        isError = isError || spanDecorator.getIsError();
    }

    @Override
    public void parseExit(SpanDecorator spanDecorator, int applicationId, int applicationInstanceId, String segmentId) {
        isError = isError || spanDecorator.getIsError();
    }

    @Override
    public void parseLocal(SpanDecorator spanDecorator, int applicationId, int applicationInstanceId,
        String segmentId) {
        isError = isError || spanDecorator.getIsError();
    }

    @Override public void build() {
        logger.debug("segment cost listener build");
        StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);

        for (SegmentCostDataDefine.SegmentCost segmentCost : segmentCosts) {
            segmentCost.setError(isError);
            segmentCost.setTimeBucket(timeBucket);
            try {
                logger.debug("send to segment cost persistence worker, id: {}", segmentCost.getId());
                context.getClusterWorkerContext().lookup(SegmentCostPersistenceWorker.WorkerRole.INSTANCE).tell(segmentCost.toData());
            } catch (WorkerInvokeException | WorkerNotFoundException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }
}
