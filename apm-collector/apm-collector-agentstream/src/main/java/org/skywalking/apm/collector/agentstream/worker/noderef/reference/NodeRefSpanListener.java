package org.skywalking.apm.collector.agentstream.worker.noderef.reference;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.agentstream.worker.cache.InstanceCache;
import org.skywalking.apm.collector.storage.define.noderef.NodeRefDataDefine;
import org.skywalking.apm.collector.agentstream.worker.segment.EntrySpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.ExitSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.FirstSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.RefsListener;
import org.skywalking.apm.collector.agentstream.worker.util.ExchangeMarkUtils;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
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
public class NodeRefSpanListener implements EntrySpanListener, ExitSpanListener, FirstSpanListener, RefsListener {

    private final Logger logger = LoggerFactory.getLogger(NodeRefSpanListener.class);

    private List<String> nodeReferences = new ArrayList<>();
    private List<String> nodeEntryReferences = new ArrayList<>();
    private long timeBucket;
    private boolean hasReference = false;

    @Override
    public void parseExit(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        String front = ExchangeMarkUtils.INSTANCE.buildMarkedID(applicationId);
        String behind = spanObject.getPeer();
        if (spanObject.getPeerId() != 0) {
            behind = ExchangeMarkUtils.INSTANCE.buildMarkedID(spanObject.getPeerId());
        }

        String agg = front + Const.ID_SPLIT + behind;
        nodeReferences.add(agg);
    }

    @Override
    public void parseEntry(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        String behind = ExchangeMarkUtils.INSTANCE.buildMarkedID(applicationId);
        String front = ExchangeMarkUtils.INSTANCE.buildMarkedID(Const.USER_ID);
        String agg = front + Const.ID_SPLIT + behind;
        nodeEntryReferences.add(agg);
    }

    @Override
    public void parseFirst(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        timeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanObject.getStartTime());
    }

    @Override public void parseRef(TraceSegmentReference reference, int applicationId, int applicationInstanceId,
        String segmentId) {
        int parentApplicationId = InstanceCache.get(reference.getParentApplicationInstanceId());

        String front = ExchangeMarkUtils.INSTANCE.buildMarkedID(parentApplicationId);
        String behind = ExchangeMarkUtils.INSTANCE.buildMarkedID(applicationId);

        String agg = front + Const.ID_SPLIT + behind;
        nodeReferences.add(agg);

        hasReference = true;
    }

    @Override public void build() {
        logger.debug("node reference listener build");
        StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);
        if (!hasReference) {
            nodeReferences.addAll(nodeEntryReferences);
        }

        for (String agg : nodeReferences) {
            NodeRefDataDefine.NodeReference nodeReference = new NodeRefDataDefine.NodeReference();
            nodeReference.setId(timeBucket + Const.ID_SPLIT + agg);
            nodeReference.setAgg(agg);
            nodeReference.setTimeBucket(timeBucket);

            try {
                logger.debug("send to node reference aggregation worker, id: {}", nodeReference.getId());
                context.getClusterWorkerContext().lookup(NodeRefAggregationWorker.WorkerRole.INSTANCE).tell(nodeReference.toData());
            } catch (WorkerInvokeException | WorkerNotFoundException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }
}
