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

package org.skywalking.apm.collector.agentstream.worker.instance.performance;

import org.skywalking.apm.collector.agentstream.worker.segment.EntrySpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.FirstSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.standardization.SpanDecorator;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.define.instance.InstPerformanceDataDefine;
import org.skywalking.apm.collector.stream.StreamModuleContext;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
import org.skywalking.apm.collector.stream.worker.WorkerInvokeException;
import org.skywalking.apm.collector.stream.worker.WorkerNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstPerformanceSpanListener implements EntrySpanListener, FirstSpanListener {

    private final Logger logger = LoggerFactory.getLogger(InstPerformanceSpanListener.class);

    private int applicationId;
    private int instanceId;
    private long cost;
    private long timeBucket;

    @Override
    public void parseEntry(SpanDecorator spanDecorator, int applicationId, int applicationInstanceId,
        String segmentId) {
    }

    @Override
    public void parseFirst(SpanDecorator spanDecorator, int applicationId, int applicationInstanceId,
        String segmentId) {
        this.applicationId = applicationId;
        this.instanceId = applicationInstanceId;
        this.cost = spanDecorator.getEndTime() - spanDecorator.getStartTime();
        timeBucket = TimeBucketUtils.INSTANCE.getSecondTimeBucket(spanDecorator.getStartTime());
    }

    @Override public void build() {
        StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);

        InstPerformanceDataDefine.InstPerformance instPerformance = new InstPerformanceDataDefine.InstPerformance();
        instPerformance.setId(timeBucket + Const.ID_SPLIT + instanceId);
        instPerformance.setApplicationId(applicationId);
        instPerformance.setInstanceId(instanceId);
        instPerformance.setCalls(1);
        instPerformance.setCostTotal(cost);
        instPerformance.setTimeBucket(timeBucket);

        try {
            logger.debug("send to instance performance persistence worker, id: {}", instPerformance.getId());
            context.getClusterWorkerContext().lookup(InstPerformancePersistenceWorker.WorkerRole.INSTANCE).tell(instPerformance.toData());
        } catch (WorkerInvokeException | WorkerNotFoundException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
