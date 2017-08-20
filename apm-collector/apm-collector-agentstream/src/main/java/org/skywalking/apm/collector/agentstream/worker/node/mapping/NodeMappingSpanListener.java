package org.skywalking.apm.collector.agentstream.worker.node.mapping;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.storage.define.node.NodeMappingDataDefine;
import org.skywalking.apm.collector.agentstream.worker.segment.FirstSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.RefsListener;
import org.skywalking.apm.collector.agentstream.worker.util.ExchangeMarkUtils;
import org.skywalking.apm.collector.stream.worker.util.TimeBucketUtils;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
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

    private List<String> nodeMappings = new ArrayList<>();
    private long timeBucket;

    @Override public void parseRef(TraceSegmentReference reference, int applicationId, int applicationInstanceId,
        String segmentId) {
        logger.debug("node mapping listener parse reference");
        String peers = reference.getNetworkAddress();
        if (reference.getNetworkAddressId() != 0) {
            peers = ExchangeMarkUtils.INSTANCE.buildMarkedID(reference.getNetworkAddressId());
        }

        String agg = ExchangeMarkUtils.INSTANCE.buildMarkedID(applicationId) + Const.ID_SPLIT + peers;
        nodeMappings.add(agg);
    }

    @Override
    public void parseFirst(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        timeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanObject.getStartTime());
    }

    @Override public void build() {
        logger.debug("node mapping listener build");
        StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);
        for (String agg : nodeMappings) {
            NodeMappingDataDefine.NodeMapping nodeMapping = new NodeMappingDataDefine.NodeMapping();
            nodeMapping.setId(timeBucket + Const.ID_SPLIT + agg);
            nodeMapping.setAgg(agg);
            nodeMapping.setTimeBucket(timeBucket);

            try {
                logger.debug("send to node mapping aggregation worker, id: {}", nodeMapping.getId());
                context.getClusterWorkerContext().lookup(NodeMappingAggregationWorker.WorkerRole.INSTANCE).tell(nodeMapping.toData());
            } catch (WorkerInvokeException | WorkerNotFoundException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }
}
