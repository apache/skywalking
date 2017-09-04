package org.skywalking.apm.collector.agentstream.worker.noderef;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.agentstream.worker.cache.InstanceCache;
import org.skywalking.apm.collector.agentstream.worker.segment.EntrySpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.ExitSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.FirstSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.RefsListener;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.define.noderef.NodeReferenceDataDefine;
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
public class NodeReferenceSpanListener implements EntrySpanListener, ExitSpanListener, FirstSpanListener, RefsListener {

    private final Logger logger = LoggerFactory.getLogger(NodeReferenceSpanListener.class);

    private List<NodeReferenceDataDefine.NodeReference> nodeExitReferences = new ArrayList<>();
    private List<NodeReferenceDataDefine.NodeReference> nodeEntryReferences = new ArrayList<>();
    private List<NodeReferenceDataDefine.NodeReference> nodeReferences = new ArrayList<>();
    private long timeBucket;
    private boolean hasReference = false;
    private long startTime;
    private long endTime;
    private boolean isError;

    @Override
    public void parseExit(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        NodeReferenceDataDefine.NodeReference referenceSum = new NodeReferenceDataDefine.NodeReference();
        referenceSum.setFrontApplicationId(applicationId);
        referenceSum.setBehindApplicationId(spanObject.getPeerId());

        String id = String.valueOf(applicationId);
        if (spanObject.getPeerId() != 0) {
            referenceSum.setBehindPeer(Const.EMPTY_STRING);
            id = id + Const.ID_SPLIT + String.valueOf(spanObject.getPeerId());
        } else {
            referenceSum.setBehindPeer(spanObject.getPeer());
            id = id + Const.ID_SPLIT + spanObject.getPeer();
        }
        referenceSum.setId(id);
        nodeExitReferences.add(buildNodeRefSum(referenceSum, spanObject.getStartTime(), spanObject.getEndTime(), spanObject.getIsError()));
    }

    @Override
    public void parseEntry(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        NodeReferenceDataDefine.NodeReference referenceSum = new NodeReferenceDataDefine.NodeReference();
        referenceSum.setFrontApplicationId(Const.USER_ID);
        referenceSum.setBehindApplicationId(applicationId);
        referenceSum.setBehindPeer(Const.EMPTY_STRING);

        String id = String.valueOf(Const.USER_ID) + Const.ID_SPLIT + String.valueOf(applicationId);
        referenceSum.setId(id);
        nodeEntryReferences.add(buildNodeRefSum(referenceSum, spanObject.getStartTime(), spanObject.getEndTime(), spanObject.getIsError()));
    }

    private NodeReferenceDataDefine.NodeReference buildNodeRefSum(NodeReferenceDataDefine.NodeReference referenceSum,
        long startTime, long endTime, boolean isError) {
        long cost = endTime - startTime;
        if (cost <= 1000 && !isError) {
            referenceSum.setS1LTE(1);
        } else if (1000 < cost && cost <= 3000 && !isError) {
            referenceSum.setS3LTE(1);
        } else if (3000 < cost && cost <= 5000 && !isError) {
            referenceSum.setS5LTE(1);
        } else if (5000 < cost && !isError) {
            referenceSum.setS5GT(1);
        } else {
            referenceSum.setError(1);
        }
        referenceSum.setSummary(1);
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

        NodeReferenceDataDefine.NodeReference referenceSum = new NodeReferenceDataDefine.NodeReference();
        referenceSum.setFrontApplicationId(parentApplicationId);
        referenceSum.setBehindApplicationId(applicationId);
        referenceSum.setBehindPeer(Const.EMPTY_STRING);

        String id = String.valueOf(parentApplicationId) + Const.ID_SPLIT + String.valueOf(applicationId);
        referenceSum.setId(id);

        hasReference = true;
        nodeReferences.add(referenceSum);
    }

    @Override public void build() {
        logger.debug("node reference summary listener build");
        StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);
        if (!hasReference) {
            nodeExitReferences.addAll(nodeEntryReferences);
        } else {
            nodeReferences.forEach(referenceSum -> {
                nodeExitReferences.add(buildNodeRefSum(referenceSum, startTime, endTime, isError));
            });
        }

        for (NodeReferenceDataDefine.NodeReference referenceSum : nodeExitReferences) {
            referenceSum.setId(timeBucket + Const.ID_SPLIT + referenceSum.getId());
            referenceSum.setTimeBucket(timeBucket);

            try {
                logger.debug("send to node reference summary aggregation worker, id: {}", referenceSum.getId());
                context.getClusterWorkerContext().lookup(NodeReferenceAggregationWorker.WorkerRole.INSTANCE).tell(referenceSum.toData());
            } catch (WorkerInvokeException | WorkerNotFoundException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }
}
