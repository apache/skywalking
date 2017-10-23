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

package org.skywalking.apm.collector.agentstream.worker.global;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.agentstream.worker.segment.FirstSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.GlobalTraceIdsListener;
import org.skywalking.apm.collector.agentstream.worker.segment.standardization.SpanDecorator;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.define.global.GlobalTraceDataDefine;
import org.skywalking.apm.collector.stream.StreamModuleContext;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
import org.skywalking.apm.collector.stream.worker.WorkerInvokeException;
import org.skywalking.apm.collector.stream.worker.WorkerNotFoundException;
import org.skywalking.apm.network.proto.UniqueId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class GlobalTraceSpanListener implements FirstSpanListener, GlobalTraceIdsListener {

    private final Logger logger = LoggerFactory.getLogger(GlobalTraceSpanListener.class);

    private List<String> globalTraceIds = new ArrayList<>();
    private String segmentId;
    private long timeBucket;

    @Override
    public void parseFirst(SpanDecorator spanDecorator, int applicationId, int applicationInstanceId,
        String segmentId) {
        this.timeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanDecorator.getStartTime());
        this.segmentId = segmentId;
    }

    @Override public void parseGlobalTraceId(UniqueId uniqueId) {
        StringBuilder globalTraceIdBuilder = new StringBuilder();
        for (int i = 0; i < uniqueId.getIdPartsList().size(); i++) {
            if (i == 0) {
                globalTraceIdBuilder.append(uniqueId.getIdPartsList().get(i));
            } else {
                globalTraceIdBuilder.append(".").append(uniqueId.getIdPartsList().get(i));
            }
        }
        globalTraceIds.add(globalTraceIdBuilder.toString());
    }

    @Override public void build() {
        logger.debug("global trace listener build");
        StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);

        for (String globalTraceId : globalTraceIds) {
            GlobalTraceDataDefine.GlobalTrace globalTrace = new GlobalTraceDataDefine.GlobalTrace();
            globalTrace.setGlobalTraceId(globalTraceId);
            globalTrace.setId(segmentId + Const.ID_SPLIT + globalTraceId);
            globalTrace.setSegmentId(segmentId);
            globalTrace.setTimeBucket(timeBucket);
            try {
                logger.debug("send to global trace persistence worker, id: {}", globalTrace.getId());
                context.getClusterWorkerContext().lookup(GlobalTracePersistenceWorker.WorkerRole.INSTANCE).tell(globalTrace.toData());
            } catch (WorkerInvokeException | WorkerNotFoundException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }
}