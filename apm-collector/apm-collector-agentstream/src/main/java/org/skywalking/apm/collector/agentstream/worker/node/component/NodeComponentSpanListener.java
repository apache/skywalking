package org.skywalking.apm.collector.agentstream.worker.node.component;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.agentstream.worker.segment.EntrySpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.ExitSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.FirstSpanListener;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.define.node.NodeComponentDataDefine;
import org.skywalking.apm.collector.stream.StreamModuleContext;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
import org.skywalking.apm.collector.stream.worker.WorkerInvokeException;
import org.skywalking.apm.collector.stream.worker.WorkerNotFoundException;
import org.skywalking.apm.network.proto.SpanObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class NodeComponentSpanListener implements EntrySpanListener, ExitSpanListener, FirstSpanListener {

    private final Logger logger = LoggerFactory.getLogger(NodeComponentSpanListener.class);

    private List<NodeComponentDataDefine.NodeComponent> nodeComponents = new ArrayList<>();
    private long timeBucket;

    @Override
    public void parseExit(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        NodeComponentDataDefine.NodeComponent nodeComponent = new NodeComponentDataDefine.NodeComponent();
        nodeComponent.setComponentId(spanObject.getComponentId());

        String id;
        if (spanObject.getComponentId() == 0) {
            nodeComponent.setComponentName(spanObject.getComponent());
            id = nodeComponent.getComponentName();
        } else {
            nodeComponent.setComponentName(Const.EMPTY_STRING);
            id = String.valueOf(nodeComponent.getComponentId());
        }

        nodeComponent.setPeerId(spanObject.getPeerId());
        if (spanObject.getPeerId() == 0) {
            nodeComponent.setPeer(spanObject.getPeer());
            id = id + Const.ID_SPLIT + nodeComponent.getPeer();
        } else {
            nodeComponent.setPeer(Const.EMPTY_STRING);
            id = id + Const.ID_SPLIT + nodeComponent.getPeerId();
        }
        nodeComponent.setId(id);
        nodeComponents.add(nodeComponent);
    }

    @Override
    public void parseEntry(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        NodeComponentDataDefine.NodeComponent nodeComponent = new NodeComponentDataDefine.NodeComponent();
        nodeComponent.setComponentId(spanObject.getComponentId());

        String id;
        if (spanObject.getComponentId() == 0) {
            nodeComponent.setComponentName(spanObject.getComponent());
            id = nodeComponent.getComponentName();
        } else {
            id = String.valueOf(nodeComponent.getComponentId());
            nodeComponent.setComponentName(Const.EMPTY_STRING);
        }

        nodeComponent.setPeerId(applicationId);
        nodeComponent.setPeer(Const.EMPTY_STRING);
        id = id + Const.ID_SPLIT + String.valueOf(applicationId);
        nodeComponent.setId(id);

        nodeComponents.add(nodeComponent);
    }

    @Override
    public void parseFirst(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        timeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanObject.getStartTime());
    }

    @Override public void build() {
        StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);

        nodeComponents.forEach(nodeComponent -> {
            nodeComponent.setId(timeBucket + Const.ID_SPLIT + nodeComponent.getId());
            nodeComponent.setTimeBucket(timeBucket);

            try {
                logger.debug("send to node component aggregation worker, id: {}", nodeComponent.getId());
                context.getClusterWorkerContext().lookup(NodeComponentAggregationWorker.WorkerRole.INSTANCE).tell(nodeComponent.toData());
            } catch (WorkerInvokeException | WorkerNotFoundException e) {
                logger.error(e.getMessage(), e);
            }
        });
    }
}
