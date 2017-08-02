package org.skywalking.apm.collector.agentstream.worker.node.component;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.agentstream.worker.Const;
import org.skywalking.apm.collector.agentstream.worker.cache.ComponentCache;
import org.skywalking.apm.collector.agentstream.worker.node.component.define.NodeComponentDataDefine;
import org.skywalking.apm.collector.agentstream.worker.segment.EntrySpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.ExitSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.LocalSpanListener;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.stream.StreamModuleContext;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
import org.skywalking.apm.collector.stream.worker.WorkerInvokeException;
import org.skywalking.apm.collector.stream.worker.WorkerNotFoundException;
import org.skywalking.apm.network.proto.SpanObject;
import org.skywalking.apm.network.trace.component.ComponentsDefine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class NodeComponentSpanListener implements EntrySpanListener, ExitSpanListener, LocalSpanListener {

    private final Logger logger = LoggerFactory.getLogger(NodeComponentSpanListener.class);

    private List<NodeComponentDataDefine.NodeComponent> nodeComponents = new ArrayList<>();

    @Override
    public void parseExit(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        String componentName = ComponentsDefine.getComponentName(spanObject.getComponentId());
        createNodeComponent(spanObject, applicationId, componentName);
    }

    @Override
    public void parseEntry(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        String componentName = ComponentsDefine.getComponentName(spanObject.getComponentId());
        createNodeComponent(spanObject, applicationId, componentName);
    }

    @Override
    public void parseLocal(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        int componentId = ComponentCache.get(applicationId, spanObject.getComponent());

        NodeComponentDataDefine.NodeComponent nodeComponent = new NodeComponentDataDefine.NodeComponent();
        nodeComponent.setApplicationId(applicationId);
        nodeComponent.setComponentId(componentId);
        nodeComponent.setComponentName(spanObject.getComponent());

        if (componentId == 0) {
            StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);

            logger.debug("send to node component exchange worker, id: {}", nodeComponent.getId());
            nodeComponent.setId(applicationId + Const.ID_SPLIT + spanObject.getComponent());
            try {
                context.getClusterWorkerContext().lookup(NodeComponentExchangeWorker.WorkerRole.INSTANCE).tell(nodeComponent.toData());
            } catch (WorkerInvokeException | WorkerNotFoundException e) {
                logger.error(e.getMessage(), e);
            }
        } else {
            nodeComponent.setId(applicationId + Const.ID_SPLIT + componentId);
            nodeComponents.add(nodeComponent);
        }
    }

    private void createNodeComponent(SpanObject spanObject, int applicationId, String componentName) {
        NodeComponentDataDefine.NodeComponent nodeComponent = new NodeComponentDataDefine.NodeComponent();
        nodeComponent.setApplicationId(applicationId);
        nodeComponent.setComponentId(spanObject.getComponentId());
        nodeComponent.setComponentName(componentName);
        nodeComponent.setId(applicationId + Const.ID_SPLIT + spanObject.getComponentId());
        nodeComponents.add(nodeComponent);
    }

    @Override public void build() {
        StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);
        for (NodeComponentDataDefine.NodeComponent nodeComponent : nodeComponents) {
            try {
                logger.debug("send to node component aggregation worker, id: {}", nodeComponent.getId());
                context.getClusterWorkerContext().lookup(NodeComponentAggregationWorker.WorkerRole.INSTANCE).tell(nodeComponent.toData());
            } catch (WorkerInvokeException | WorkerNotFoundException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }
}
