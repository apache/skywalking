package org.skywalking.apm.collector.agentstream.worker.noderef.summary;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.stream.worker.util.Const;
import org.skywalking.apm.collector.agentstream.worker.cache.InstanceCache;
import org.skywalking.apm.collector.agentstream.worker.noderef.summary.define.NodeRefSumDataDefine;
import org.skywalking.apm.collector.agentstream.worker.segment.EntrySpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.ExitSpanListener;
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
public class NodeRefSumSpanListener implements EntrySpanListener, ExitSpanListener, FirstSpanListener, RefsListener {

    private final Logger logger = LoggerFactory.getLogger(NodeRefSumSpanListener.class);

    private List<NodeRefSumDataDefine.NodeReferenceSum> nodeExitReferences = new ArrayList<>();
    private List<NodeRefSumDataDefine.NodeReferenceSum> nodeEntryReferences = new ArrayList<>();
    private List<String> nodeReferences = new ArrayList<>();
    private long timeBucket;
    private boolean hasReference = false;
    private long startTime;
    private long endTime;
    private boolean isError;

    @Override
    public void parseExit(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        String front = ExchangeMarkUtils.INSTANCE.buildMarkedID(applicationId);
        String behind = spanObject.getPeer();
        if (spanObject.getPeerId() != 0) {
            behind = ExchangeMarkUtils.INSTANCE.buildMarkedID(spanObject.getPeerId());
        }

        String agg = front + Const.ID_SPLIT + behind;
        nodeExitReferences.add(buildNodeRefSum(spanObject.getStartTime(), spanObject.getEndTime(), agg, spanObject.getIsError()));
    }

    @Override
    public void parseEntry(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        String behind = ExchangeMarkUtils.INSTANCE.buildMarkedID(applicationId);
        String front = ExchangeMarkUtils.INSTANCE.buildMarkedID(Const.USER_ID);
        String agg = front + Const.ID_SPLIT + behind;
        nodeEntryReferences.add(buildNodeRefSum(spanObject.getStartTime(), spanObject.getEndTime(), agg, spanObject.getIsError()));
    }

    private NodeRefSumDataDefine.NodeReferenceSum buildNodeRefSum(long startTime, long endTime, String agg,
        boolean isError) {
        NodeRefSumDataDefine.NodeReferenceSum referenceSum = new NodeRefSumDataDefine.NodeReferenceSum();
        referenceSum.setAgg(agg);

        long cost = endTime - startTime;
        if (cost <= 1000 && !isError) {
            referenceSum.setOneSecondLess(1L);
        } else if (1000 < cost && cost <= 3000 && !isError) {
            referenceSum.setThreeSecondLess(1L);
        } else if (3000 < cost && cost <= 5000 && !isError) {
            referenceSum.setFiveSecondLess(1L);
        } else if (5000 < cost && !isError) {
            referenceSum.setFiveSecondGreater(1L);
        } else {
            referenceSum.setError(1L);
        }
        referenceSum.setSummary(1L);
        return referenceSum;
    }

    @Override
    public void parseFirst(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        timeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanObject.getStartTime());
        startTime = spanObject.getStartTime();
        endTime = spanObject.getEndTime();
        isError = spanObject.getIsError();
    }

    @Override public void parseRef(TraceSegmentReference reference, int applicationId, int applicationInstanceId,
        String segmentId) {
        int parentApplicationId = InstanceCache.get(reference.getParentApplicationInstanceId());

        String front = ExchangeMarkUtils.INSTANCE.buildMarkedID(parentApplicationId);
        String behind = ExchangeMarkUtils.INSTANCE.buildMarkedID(applicationId);

        String agg = front + Const.ID_SPLIT + behind;

        hasReference = true;
        nodeReferences.add(agg);
    }

    @Override public void build() {
        logger.debug("node reference summary listener build");
        StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);
        if (!hasReference) {
            nodeExitReferences.addAll(nodeEntryReferences);
        } else {
            nodeReferences.forEach(agg -> {
                nodeExitReferences.add(buildNodeRefSum(startTime, endTime, agg, isError));
            });
        }

        for (NodeRefSumDataDefine.NodeReferenceSum referenceSum : nodeExitReferences) {
            referenceSum.setId(timeBucket + Const.ID_SPLIT + referenceSum.getAgg());
            referenceSum.setTimeBucket(timeBucket);

            try {
                logger.debug("send to node reference summary aggregation worker, id: {}", referenceSum.getId());
                context.getClusterWorkerContext().lookup(NodeRefSumAggregationWorker.WorkerRole.INSTANCE).tell(referenceSum.toData());
            } catch (WorkerInvokeException | WorkerNotFoundException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }
}
