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

package org.skywalking.apm.collector.agentstream.worker.serviceref;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.collector.agentstream.worker.segment.EntrySpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.FirstSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.RefsListener;
import org.skywalking.apm.collector.agentstream.worker.segment.standardization.ReferenceDecorator;
import org.skywalking.apm.collector.agentstream.worker.segment.standardization.SpanDecorator;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.define.serviceref.ServiceReferenceDataDefine;
import org.skywalking.apm.collector.stream.StreamModuleContext;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
import org.skywalking.apm.collector.stream.worker.WorkerInvokeException;
import org.skywalking.apm.collector.stream.worker.WorkerNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ServiceReferenceSpanListener implements FirstSpanListener, EntrySpanListener, RefsListener {

    private final Logger logger = LoggerFactory.getLogger(ServiceReferenceSpanListener.class);

    private List<ReferenceDecorator> referenceServices = new LinkedList<>();
    private int serviceId = 0;
    private long startTime = 0;
    private long endTime = 0;
    private boolean isError = false;
    private long timeBucket;
    private boolean hasEntry = false;

    @Override
    public void parseFirst(SpanDecorator spanDecorator, int applicationId, int applicationInstanceId,
        String segmentId) {
        timeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanDecorator.getStartTime());
    }

    @Override public void parseRef(ReferenceDecorator referenceDecorator, int applicationId, int applicationInstanceId,
        String segmentId) {
        referenceServices.add(referenceDecorator);
    }

    @Override
    public void parseEntry(SpanDecorator spanDecorator, int applicationId, int applicationInstanceId,
        String segmentId) {
        serviceId = spanDecorator.getOperationNameId();
        startTime = spanDecorator.getStartTime();
        endTime = spanDecorator.getEndTime();
        isError = spanDecorator.getIsError();
        this.hasEntry = true;
    }

    private void calculateCost(ServiceReferenceDataDefine.ServiceReference serviceReference, long startTime,
        long endTime,
        boolean isError) {
        long cost = endTime - startTime;
        if (cost <= 1000 && !isError) {
            serviceReference.setS1Lte(1);
        } else if (1000 < cost && cost <= 3000 && !isError) {
            serviceReference.setS3Lte(1);
        } else if (3000 < cost && cost <= 5000 && !isError) {
            serviceReference.setS5Lte(1);
        } else if (5000 < cost && !isError) {
            serviceReference.setS5Gt(1);
        } else {
            serviceReference.setError(1);
        }
        serviceReference.setSummary(1);
        serviceReference.setCostSummary(cost);
    }

    @Override public void build() {
        logger.debug("service reference listener build");
        if (hasEntry) {
            StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);

            if (referenceServices.size() > 0) {
                referenceServices.forEach(reference -> {
                    ServiceReferenceDataDefine.ServiceReference serviceReference = new ServiceReferenceDataDefine.ServiceReference();
                    int entryServiceId = reference.getEntryServiceId();
                    int frontServiceId = reference.getParentServiceId();
                    int behindServiceId = serviceId;
                    calculateCost(serviceReference, startTime, endTime, isError);

                    logger.debug("has reference, entryServiceId: {}", entryServiceId);
                    sendToAggregationWorker(context, serviceReference, entryServiceId, frontServiceId, behindServiceId);
                });
            } else {
                ServiceReferenceDataDefine.ServiceReference serviceReference = new ServiceReferenceDataDefine.ServiceReference();
                int entryServiceId = serviceId;
                int frontServiceId = Const.NONE_SERVICE_ID;
                int behindServiceId = serviceId;

                calculateCost(serviceReference, startTime, endTime, isError);
                sendToAggregationWorker(context, serviceReference, entryServiceId, frontServiceId, behindServiceId);
            }
        }
    }

    private void sendToAggregationWorker(StreamModuleContext context,
        ServiceReferenceDataDefine.ServiceReference serviceReference, int entryServiceId, int frontServiceId,
        int behindServiceId) {
        StringBuilder idBuilder = new StringBuilder();
        idBuilder.append(timeBucket).append(Const.ID_SPLIT);

        idBuilder.append(entryServiceId).append(Const.ID_SPLIT);
        serviceReference.setEntryServiceId(entryServiceId);
        serviceReference.setEntryServiceName(Const.EMPTY_STRING);

        idBuilder.append(frontServiceId).append(Const.ID_SPLIT);
        serviceReference.setFrontServiceId(frontServiceId);
        serviceReference.setFrontServiceName(Const.EMPTY_STRING);

        idBuilder.append(behindServiceId);
        serviceReference.setBehindServiceId(behindServiceId);
        serviceReference.setBehindServiceName(Const.EMPTY_STRING);

        serviceReference.setId(idBuilder.toString());
        serviceReference.setTimeBucket(timeBucket);
        try {
            logger.debug("send to service reference aggregation worker, id: {}", serviceReference.getId());
            context.getClusterWorkerContext().lookup(ServiceReferenceAggregationWorker.WorkerRole.INSTANCE).tell(serviceReference.toData());
        } catch (WorkerInvokeException | WorkerNotFoundException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
