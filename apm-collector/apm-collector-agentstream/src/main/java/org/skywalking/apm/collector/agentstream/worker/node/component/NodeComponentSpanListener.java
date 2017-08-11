package org.skywalking.apm.collector.agentstream.worker.node.component;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.agentstream.worker.node.component.define.NodeComponentDataDefine;
import org.skywalking.apm.collector.agentstream.worker.segment.EntrySpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.ExitSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.FirstSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.LocalSpanListener;
import org.skywalking.apm.collector.agentstream.worker.util.ExchangeMarkUtils;
import org.skywalking.apm.collector.stream.worker.util.TimeBucketUtils;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
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
public class NodeComponentSpanListener implements EntrySpanListener, ExitSpanListener, FirstSpanListener, LocalSpanListener {

    private final Logger logger = LoggerFactory.getLogger(NodeComponentSpanListener.class);

    private List<String> nodeComponents = new ArrayList<>();
    private long timeBucket;

    @Override
    public void parseExit(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        String componentName = ExchangeMarkUtils.INSTANCE.buildMarkedID(spanObject.getComponentId());
        if (spanObject.getComponentId() == 0) {
            componentName = spanObject.getComponent();
        }
        String peer = ExchangeMarkUtils.INSTANCE.buildMarkedID(spanObject.getPeerId());
        if (spanObject.getPeerId() == 0) {
            peer = spanObject.getPeer();
        }

        String agg = peer + Const.ID_SPLIT + componentName;
        nodeComponents.add(agg);
    }

    @Override
    public void parseEntry(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        buildEntryOrLocal(spanObject, applicationId);
    }

    @Override
    public void parseLocal(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        buildEntryOrLocal(spanObject, applicationId);
    }

    private void buildEntryOrLocal(SpanObject spanObject, int applicationId) {
        String componentName = ExchangeMarkUtils.INSTANCE.buildMarkedID(spanObject.getComponentId());

        if (spanObject.getComponentId() == 0) {
            componentName = spanObject.getComponent();
        }

        String peer = ExchangeMarkUtils.INSTANCE.buildMarkedID(applicationId);
        String agg = peer + Const.ID_SPLIT + componentName;
        nodeComponents.add(agg);
    }

    @Override
    public void parseFirst(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        timeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanObject.getStartTime());
    }

    @Override public void build() {
        StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);

        nodeComponents.forEach(agg -> {
            NodeComponentDataDefine.NodeComponent nodeComponent = new NodeComponentDataDefine.NodeComponent();
            nodeComponent.setId(timeBucket + Const.ID_SPLIT + agg);
            nodeComponent.setAgg(agg);
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
