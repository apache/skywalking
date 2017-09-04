package org.skywalking.apm.collector.agentstream.worker.node.mapping;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.agentstream.worker.segment.FirstSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.RefsListener;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.define.node.NodeMappingDataDefine;
import org.skywalking.apm.collector.stream.StreamModuleContext;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
import org.skywalking.apm.collector.stream.worker.WorkerInvokeException;
import org.skywalking.apm.collector.stream.worker.WorkerNotFoundException;
import org.skywalking.apm.network.proto.SpanObject;
import org.skywalking.apm.network.proto.TraceSegmentReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class NodeMappingSpanListener implements RefsListener, FirstSpanListener {

    private final Logger logger = LoggerFactory.getLogger(NodeMappingSpanListener.class);

    private List<NodeMappingDataDefine.NodeMapping> nodeMappings = new ArrayList<>();
    private long timeBucket;

    @Override public void parseRef(TraceSegmentReference reference, int applicationId, int applicationInstanceId,
        String segmentId) {
        logger.debug("node mapping listener parse reference");
        NodeMappingDataDefine.NodeMapping nodeMapping = new NodeMappingDataDefine.NodeMapping();
        nodeMapping.setApplicationId(applicationId);
        nodeMapping.setAddressId(reference.getNetworkAddressId());

        String id = String.valueOf(applicationId);
        if (reference.getNetworkAddressId() != 0) {
            nodeMapping.setAddress(Const.EMPTY_STRING);
            id = id + Const.ID_SPLIT + String.valueOf(nodeMapping.getAddressId());
        } else {
            id = id + Const.ID_SPLIT + reference.getNetworkAddress();
            nodeMapping.setAddress(reference.getNetworkAddress());
        }

        nodeMapping.setId(id);
        nodeMappings.add(nodeMapping);
    }

    @Override
    public void parseFirst(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        timeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanObject.getStartTime());
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
