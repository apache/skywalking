package org.skywalking.apm.collector.agentstream.worker.node.component.define;

import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.stream.worker.impl.data.Attribute;
import org.skywalking.apm.collector.stream.worker.impl.data.AttributeType;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;
import org.skywalking.apm.collector.stream.worker.impl.data.Exchange;
import org.skywalking.apm.collector.stream.worker.impl.data.Transform;
import org.skywalking.apm.collector.stream.worker.impl.data.operate.CoverOperation;
import org.skywalking.apm.collector.stream.worker.impl.data.operate.NonOperation;

/**
 * @author pengys5
 */
public class NodeComponentDataDefine extends DataDefine {

    @Override public int defineId() {
        return 101;
    }

    @Override protected int initialCapacity() {
        return 5;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(NodeComponentTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(NodeComponentTable.COLUMN_APPLICATION_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(2, new Attribute(NodeComponentTable.COLUMN_COMPONENT_NAME, AttributeType.STRING, new CoverOperation()));
        addAttribute(3, new Attribute(NodeComponentTable.COLUMN_COMPONENT_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(4, new Attribute(NodeComponentTable.COLUMN_EXCHANGE_TIMES, AttributeType.INTEGER, new NonOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        return null;
    }

    @Override public RemoteData serialize(Object object) {
        return null;
    }

    public static class NodeComponent extends Exchange implements Transform<NodeComponent> {
        private String id;
        private int applicationId;
        private String componentName;
        private int componentId;

        public NodeComponent(String id, int applicationId, String componentName, int componentId) {
            super(0);
            this.id = id;
            this.applicationId = applicationId;
            this.componentName = componentName;
            this.componentId = componentId;
        }

        public NodeComponent() {
            super(0);
        }

        @Override public Data toData() {
            NodeComponentDataDefine define = new NodeComponentDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataInteger(0, this.applicationId);
            data.setDataString(1, this.componentName);
            data.setDataInteger(1, this.componentId);
            data.setDataInteger(2, this.getTimes());
            return data;
        }

        @Override public NodeComponent toSelf(Data data) {
            this.id = data.getDataString(0);
            this.applicationId = data.getDataInteger(0);
            this.componentName = data.getDataString(1);
            this.componentId = data.getDataInteger(1);
            this.setTimes(data.getDataInteger(2));
            return this;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getComponentName() {
            return componentName;
        }

        public void setComponentName(String componentName) {
            this.componentName = componentName;
        }

        public int getComponentId() {
            return componentId;
        }

        public void setComponentId(int componentId) {
            this.componentId = componentId;
        }

        public int getApplicationId() {
            return applicationId;
        }

        public void setApplicationId(int applicationId) {
            this.applicationId = applicationId;
        }
    }
}
