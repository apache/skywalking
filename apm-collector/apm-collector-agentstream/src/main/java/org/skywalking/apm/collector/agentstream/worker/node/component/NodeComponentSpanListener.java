package org.skywalking.apm.collector.agentstream.worker.node.component;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.agentstream.worker.Const;
import org.skywalking.apm.collector.agentstream.worker.node.component.define.NodeComponentDataDefine;
import org.skywalking.apm.collector.agentstream.worker.segment.EntrySpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.ExitSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.FirstSpanListener;
import org.skywalking.apm.collector.agentstream.worker.util.TimeBucketUtils;
import org.skywalking.apm.network.proto.SpanObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class NodeComponentSpanListener implements EntrySpanListener, ExitSpanListener, FirstSpanListener {

    private final Logger logger = LoggerFactory.getLogger(NodeComponentSpanListener.class);

    private List<String> nodeComponents = new ArrayList<>();
    private long timeBucket;

    @Override public void parseExit(SpanObject spanObject, int applicationId, int applicationInstanceId) {
        String peers = spanObject.getPeer();
        if (spanObject.getPeerId() == 0) {
            peers = String.valueOf(spanObject.getPeerId());
        }
        String agg = spanObject.getComponent() + Const.ID_SPLIT + peers;
        nodeComponents.add(agg);
    }

    @Override public void parseEntry(SpanObject spanObject, int applicationId, int applicationInstanceId) {
        String peers = String.valueOf(applicationId);
        String agg = spanObject.getComponent() + Const.ID_SPLIT + peers;
        nodeComponents.add(agg);
    }

    @Override public void parseFirst(SpanObject spanObject, int applicationId, int applicationInstanceId) {
        timeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanObject.getStartTime());
    }

    @Override public void build() {
        for (String agg : nodeComponents) {
            NodeComponentDataDefine.NodeComponent nodeComponent = new NodeComponentDataDefine.NodeComponent();
            nodeComponent.setId(timeBucket + Const.ID_SPLIT + agg);
            nodeComponent.setAgg(agg);
            nodeComponent.setTimeBucket(timeBucket);
        }
    }
}
