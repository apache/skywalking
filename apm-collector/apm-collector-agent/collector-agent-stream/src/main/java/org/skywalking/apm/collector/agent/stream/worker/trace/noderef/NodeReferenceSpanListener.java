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

package org.skywalking.apm.collector.agent.stream.worker.trace.noderef;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.collector.agent.stream.parser.EntrySpanListener;
import org.skywalking.apm.collector.agent.stream.parser.ExitSpanListener;
import org.skywalking.apm.collector.agent.stream.parser.RefsListener;
import org.skywalking.apm.collector.agent.stream.parser.standardization.ReferenceDecorator;
import org.skywalking.apm.collector.agent.stream.parser.standardization.SpanDecorator;
import org.skywalking.apm.collector.cache.CacheServiceManager;
import org.skywalking.apm.collector.core.util.CollectionUtils;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.table.noderef.NodeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class NodeReferenceSpanListener implements EntrySpanListener, ExitSpanListener, RefsListener {

    private final Logger logger = LoggerFactory.getLogger(NodeReferenceSpanListener.class);

    private final CacheServiceManager cacheServiceManager;
    private final List<NodeReference> nodeReferences;
    private final List<NodeReference> references;

    public NodeReferenceSpanListener(CacheServiceManager cacheServiceManager) {
        this.cacheServiceManager = cacheServiceManager;
        this.nodeReferences = new LinkedList<>();
        this.references = new LinkedList<>();
    }

    @Override
    public void parseExit(SpanDecorator spanDecorator, int applicationId, int instanceId, String segmentId) {
        NodeReference nodeReference = new NodeReference(Const.EMPTY_STRING);
        nodeReference.setFrontApplicationId(applicationId);
        nodeReference.setBehindApplicationId(spanDecorator.getPeerId());
        nodeReference.setTimeBucket(TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanDecorator.getStartTime()));

        StringBuilder idBuilder = new StringBuilder();
        idBuilder.append(nodeReference.getTimeBucket()).append(Const.ID_SPLIT).append(applicationId)
            .append(Const.ID_SPLIT).append(spanDecorator.getPeerId());

        nodeReference.setId(idBuilder.toString());
        nodeReferences.add(buildNodeRefSum(nodeReference, spanDecorator.getStartTime(), spanDecorator.getEndTime(), spanDecorator.getIsError()));
    }

    @Override
    public void parseEntry(SpanDecorator spanDecorator, int applicationId, int instanceId,
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
            NodeReference nodeReference = new NodeReference(Const.EMPTY_STRING);
            nodeReference.setFrontApplicationId(Const.USER_ID);
            nodeReference.setBehindApplicationId(applicationId);
            nodeReference.setTimeBucket(TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanDecorator.getStartTime()));

            String idBuilder = String.valueOf(nodeReference.getTimeBucket()) + Const.ID_SPLIT + nodeReference.getFrontApplicationId() +
                Const.ID_SPLIT + nodeReference.getBehindApplicationId();

            nodeReference.setId(idBuilder);
            nodeReferences.add(buildNodeRefSum(nodeReference, spanDecorator.getStartTime(), spanDecorator.getEndTime(), spanDecorator.getIsError()));
        }
    }

    @Override public void parseRef(ReferenceDecorator referenceDecorator, int applicationId, int instanceId,
        String segmentId) {
        int parentApplicationId = cacheServiceManager.getInstanceCacheService().get(referenceDecorator.getParentApplicationInstanceId());

        NodeReference referenceSum = new NodeReference(Const.EMPTY_STRING);
        referenceSum.setFrontApplicationId(parentApplicationId);
        referenceSum.setBehindApplicationId(applicationId);
        references.add(referenceSum);
    }

    @Override public void build() {
        logger.debug("node reference summary listener build");
        for (NodeReference nodeReference : nodeReferences) {
        }
    }

    private NodeReference buildNodeRefSum(NodeReference reference,
        long startTime, long endTime, boolean isError) {
        long cost = endTime - startTime;
        if (cost <= 1000 && !isError) {
            reference.setS1Lte(1);
        } else if (1000 < cost && cost <= 3000 && !isError) {
            reference.setS3Lte(1);
        } else if (3000 < cost && cost <= 5000 && !isError) {
            reference.setS5Lte(1);
        } else if (5000 < cost && !isError) {
            reference.setS5Gt(1);
        } else {
            reference.setError(1);
        }
        reference.setSummary(1);
        return reference;
    }
}
