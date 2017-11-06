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

package org.skywalking.apm.collector.agentstream.worker.noderef;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.collector.agentstream.worker.segment.EntrySpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.ExitSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.RefsListener;
import org.skywalking.apm.collector.agentstream.worker.segment.standardization.ReferenceDecorator;
import org.skywalking.apm.collector.agentstream.worker.segment.standardization.SpanDecorator;
import org.skywalking.apm.collector.cache.InstanceCache;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.util.CollectionUtils;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.define.noderef.NodeReferenceDataDefine;
import org.skywalking.apm.collector.stream.StreamModuleContext;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
import org.skywalking.apm.collector.stream.worker.WorkerInvokeException;
import org.skywalking.apm.collector.stream.worker.WorkerNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class NodeReferenceSpanListener implements EntrySpanListener, ExitSpanListener, RefsListener {

    private final Logger logger = LoggerFactory.getLogger(NodeReferenceSpanListener.class);

    private List<NodeReferenceDataDefine.NodeReference> nodeReferences = new LinkedList<>();
    private List<NodeReferenceDataDefine.NodeReference> references = new LinkedList<>();

    @Override
    public void parseExit(SpanDecorator spanDecorator, int applicationId, int applicationInstanceId, String segmentId) {
        NodeReferenceDataDefine.NodeReference nodeReference = new NodeReferenceDataDefine.NodeReference();
        nodeReference.setFrontApplicationId(applicationId);
        nodeReference.setBehindApplicationId(spanDecorator.getPeerId());
        nodeReference.setBehindPeer(Const.EMPTY_STRING);
        nodeReference.setTimeBucket(TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanDecorator.getStartTime()));

        StringBuilder idBuilder = new StringBuilder();
        idBuilder.append(nodeReference.getTimeBucket()).append(Const.ID_SPLIT).append(applicationId)
            .append(Const.ID_SPLIT).append(spanDecorator.getPeerId());

        nodeReference.setId(idBuilder.toString());
        nodeReferences.add(buildNodeRefSum(nodeReference, spanDecorator.getStartTime(), spanDecorator.getEndTime(), spanDecorator.getIsError()));
    }

    @Override
    public void parseEntry(SpanDecorator spanDecorator, int applicationId, int applicationInstanceId,
        String segmentId) {
        if (CollectionUtils.isNotEmpty(references)) {
            references.forEach(nodeReference -> {
                nodeReference.setTimeBucket(TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanDecorator.getStartTime()));
                String idBuilder = String.valueOf(nodeReference.getTimeBucket()) + Const.ID_SPLIT + nodeReference.getFrontApplicationId() +
                    Const.ID_SPLIT + nodeReference.getBehindApplicationId();

                nodeReference.setId(idBuilder);
                nodeReferences.add(buildNodeRefSum(nodeReference, spanDecorator.getStartTime(), spanDecorator.getEndTime(), spanDecorator.getIsError()));
            });
        } else {
            NodeReferenceDataDefine.NodeReference nodeReference = new NodeReferenceDataDefine.NodeReference();
            nodeReference.setFrontApplicationId(Const.USER_ID);
            nodeReference.setBehindApplicationId(applicationId);
            nodeReference.setBehindPeer(Const.EMPTY_STRING);
            nodeReference.setTimeBucket(TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanDecorator.getStartTime()));

            String idBuilder = String.valueOf(nodeReference.getTimeBucket()) + Const.ID_SPLIT + nodeReference.getFrontApplicationId() +
                Const.ID_SPLIT + nodeReference.getBehindApplicationId();

            nodeReference.setId(idBuilder);
            nodeReferences.add(buildNodeRefSum(nodeReference, spanDecorator.getStartTime(), spanDecorator.getEndTime(), spanDecorator.getIsError()));
        }
    }

    @Override public void parseRef(ReferenceDecorator referenceDecorator, int applicationId, int applicationInstanceId,
        String segmentId) {
        int parentApplicationId = InstanceCache.get(referenceDecorator.getParentApplicationInstanceId());

        NodeReferenceDataDefine.NodeReference referenceSum = new NodeReferenceDataDefine.NodeReference();
        referenceSum.setFrontApplicationId(parentApplicationId);
        referenceSum.setBehindApplicationId(applicationId);
        referenceSum.setBehindPeer(Const.EMPTY_STRING);
        references.add(referenceSum);
    }

    @Override public void build() {
        logger.debug("node reference summary listener build");
        StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);

        for (NodeReferenceDataDefine.NodeReference nodeReference : nodeReferences) {
            try {
                logger.debug("send to node reference summary aggregation worker, id: {}", nodeReference.getId());
                context.getClusterWorkerContext().lookup(NodeReferenceAggregationWorker.WorkerRole.INSTANCE).tell(nodeReference.toData());
            } catch (WorkerInvokeException | WorkerNotFoundException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    private NodeReferenceDataDefine.NodeReference buildNodeRefSum(NodeReferenceDataDefine.NodeReference reference,
        long startTime, long endTime, boolean isError) {
        long cost = endTime - startTime;
        if (cost <= 1000 && !isError) {
            reference.setS1LTE(1);
        } else if (1000 < cost && cost <= 3000 && !isError) {
            reference.setS3LTE(1);
        } else if (3000 < cost && cost <= 5000 && !isError) {
            reference.setS5LTE(1);
        } else if (5000 < cost && !isError) {
            reference.setS5GT(1);
        } else {
            reference.setError(1);
        }
        reference.setSummary(1);
        return reference;
    }
}
