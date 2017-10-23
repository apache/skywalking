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

package org.skywalking.apm.collector.agentstream.worker.service.entry;

import org.skywalking.apm.collector.agentstream.worker.segment.EntrySpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.FirstSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.RefsListener;
import org.skywalking.apm.collector.agentstream.worker.segment.standardization.ReferenceDecorator;
import org.skywalking.apm.collector.agentstream.worker.segment.standardization.SpanDecorator;
import org.skywalking.apm.collector.cache.ServiceNameCache;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.define.service.ServiceEntryDataDefine;
import org.skywalking.apm.collector.stream.StreamModuleContext;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
import org.skywalking.apm.collector.stream.worker.WorkerInvokeException;
import org.skywalking.apm.collector.stream.worker.WorkerNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ServiceEntrySpanListener implements RefsListener, FirstSpanListener, EntrySpanListener {

    private final Logger logger = LoggerFactory.getLogger(ServiceEntrySpanListener.class);

    private long timeBucket;
    private boolean hasReference = false;
    private int applicationId;
    private int entryServiceId;
    private String entryServiceName;
    private boolean hasEntry = false;

    @Override
    public void parseEntry(SpanDecorator spanDecorator, int applicationId, int applicationInstanceId,
        String segmentId) {
        this.applicationId = applicationId;
        this.entryServiceId = spanDecorator.getOperationNameId();
        this.entryServiceName = ServiceNameCache.getSplitServiceName(ServiceNameCache.get(entryServiceId));
        this.hasEntry = true;
    }

    @Override public void parseRef(ReferenceDecorator referenceDecorator, int applicationId, int applicationInstanceId,
        String segmentId) {
        hasReference = true;
    }

    @Override
    public void parseFirst(SpanDecorator spanDecorator, int applicationId, int applicationInstanceId,
        String segmentId) {
        timeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanDecorator.getStartTime());
    }

    @Override public void build() {
        logger.debug("entry service listener build");
        StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);
        if (!hasReference && hasEntry) {
            ServiceEntryDataDefine.ServiceEntry serviceEntry = new ServiceEntryDataDefine.ServiceEntry();
            serviceEntry.setId(applicationId + Const.ID_SPLIT + entryServiceId);
            serviceEntry.setApplicationId(applicationId);
            serviceEntry.setEntryServiceId(entryServiceId);
            serviceEntry.setEntryServiceName(entryServiceName);
            serviceEntry.setRegisterTime(timeBucket);
            serviceEntry.setNewestTime(timeBucket);

            try {
                logger.debug("send to service entry aggregation worker, id: {}", serviceEntry.getId());
                context.getClusterWorkerContext().lookup(ServiceEntryAggregationWorker.WorkerRole.INSTANCE).tell(serviceEntry.toData());
            } catch (WorkerInvokeException | WorkerNotFoundException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }
}
