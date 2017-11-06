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

package org.skywalking.apm.collector.agentstream.worker.node.mapping;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.agentstream.worker.segment.FirstSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.RefsListener;
import org.skywalking.apm.collector.agentstream.worker.segment.standardization.ReferenceDecorator;
import org.skywalking.apm.collector.agentstream.worker.segment.standardization.SpanDecorator;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.define.node.NodeMappingDataDefine;
import org.skywalking.apm.collector.stream.StreamModuleContext;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
import org.skywalking.apm.collector.stream.worker.WorkerInvokeException;
import org.skywalking.apm.collector.stream.worker.WorkerNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class NodeMappingSpanListener implements RefsListener, FirstSpanListener {

    private final Logger logger = LoggerFactory.getLogger(NodeMappingSpanListener.class);

    private List<NodeMappingDataDefine.NodeMapping> nodeMappings = new ArrayList<>();
    private long timeBucket;

    @Override public void parseRef(ReferenceDecorator referenceDecorator, int applicationId, int applicationInstanceId,
        String segmentId) {
        logger.debug("node mapping listener parse reference");
        NodeMappingDataDefine.NodeMapping nodeMapping = new NodeMappingDataDefine.NodeMapping();
        nodeMapping.setApplicationId(applicationId);
        nodeMapping.setAddressId(referenceDecorator.getNetworkAddressId());
        nodeMapping.setAddress(Const.EMPTY_STRING);
        String id = String.valueOf(applicationId) + Const.ID_SPLIT + String.valueOf(nodeMapping.getAddressId());
        nodeMapping.setId(id);
        nodeMappings.add(nodeMapping);
    }

    @Override
    public void parseFirst(SpanDecorator spanDecorator, int applicationId, int applicationInstanceId,
        String segmentId) {
        timeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanDecorator.getStartTime());
    }

    @Override public void build() {
        logger.debug("node mapping listener build");
        StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);

        for (NodeMappingDataDefine.NodeMapping nodeMapping : nodeMappings) {
            try {
                nodeMapping.setId(timeBucket + Const.ID_SPLIT + nodeMapping.getId());
                nodeMapping.setTimeBucket(timeBucket);
                logger.debug("send to node mapping aggregation worker, id: {}", nodeMapping.getId());
                context.getClusterWorkerContext().lookup(NodeMappingAggregationWorker.WorkerRole.INSTANCE).tell(nodeMapping.toData());
            } catch (WorkerInvokeException | WorkerNotFoundException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }
}
