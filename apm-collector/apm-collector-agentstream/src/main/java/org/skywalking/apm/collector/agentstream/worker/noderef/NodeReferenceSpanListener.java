package org.skywalking.apm.collector.agentstream.worker.noderef;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.collector.agentstream.worker.cache.InstanceCache;
import org.skywalking.apm.collector.agentstream.worker.segment.EntrySpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.ExitSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.RefsListener;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.util.CollectionUtils;
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
public class NodeReferenceSpanListener implements EntrySpanListener, ExitSpanListener, RefsListener {

    private final Logger logger = LoggerFactory.getLogger(NodeReferenceSpanListener.class);

    private List<NodeReferenceDataDefine.NodeReference> nodeReferences = new LinkedList<>();
    private List<NodeReferenceDataDefine.NodeReference> references = new LinkedList<>();

    @Override
    public void parseExit(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        NodeReferenceDataDefine.NodeReference nodeReference = new NodeReferenceDataDefine.NodeReference();
        nodeReference.setFrontApplicationId(applicationId);
        nodeReference.setBehindApplicationId(spanObject.getPeerId());
        nodeReference.setTimeBucket(TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanObject.getStartTime()));

        StringBuilder idBuilder = new StringBuilder();
        idBuilder.append(nodeReference.getTimeBucket()).append(Const.ID_SPLIT).append(applicationId);
        if (spanObject.getPeerId() != 0) {
            nodeReference.setBehindPeer(Const.EMPTY_STRING);
            idBuilder.append(Const.ID_SPLIT).append(spanObject.getPeerId());
        } else {
            nodeReference.setBehindPeer(spanObject.getPeer());
            idBuilder.append(Const.ID_SPLIT).append(spanObject.getPeer());
        }
        nodeReference.setId(idBuilder.toString());
        nodeReferences.add(buildNodeRefSum(nodeReference, spanObject.getStartTime(), spanObject.getEndTime(), spanObject.getIsError()));
    }

    @Override
    public void parseEntry(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        if (CollectionUtils.isNotEmpty(references)) {
            references.forEach(nodeReference -> {
                nodeReference.setTimeBucket(TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanObject.getStartTime()));
                String idBuilder = String.valueOf(nodeReference.getTimeBucket()) + Const.ID_SPLIT + nodeReference.getFrontApplicationId() +
                    Const.ID_SPLIT + nodeReference.getBehindApplicationId();

                nodeReference.setId(idBuilder);
                nodeReferences.add(buildNodeRefSum(nodeReference, spanObject.getStartTime(), spanObject.getEndTime(), spanObject.getIsError()));
            });
        } else {
            NodeReferenceDataDefine.NodeReference nodeReference = new NodeReferenceDataDefine.NodeReference();
            nodeReference.setFrontApplicationId(Const.USER_ID);
            nodeReference.setBehindApplicationId(applicationId);
            nodeReference.setBehindPeer(Const.EMPTY_STRING);
            nodeReference.setTimeBucket(TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanObject.getStartTime()));

            String idBuilder = String.valueOf(nodeReference.getTimeBucket()) + Const.ID_SPLIT + nodeReference.getFrontApplicationId() +
                Const.ID_SPLIT + nodeReference.getBehindApplicationId();

            nodeReference.setId(idBuilder);
            nodeReferences.add(buildNodeRefSum(nodeReference, spanObject.getStartTime(), spanObject.getEndTime(), spanObject.getIsError()));
        }
    }

    @Override public void parseRef(TraceSegmentReference reference, int applicationId, int applicationInstanceId,
        String segmentId) {
        int parentApplicationId = InstanceCache.get(reference.getParentApplicationInstanceId());

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
