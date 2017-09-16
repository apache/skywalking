package org.skywalking.apm.collector.storage.define.node;

import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.core.stream.Transform;
import org.skywalking.apm.collector.core.stream.operate.CoverOperation;
import org.skywalking.apm.collector.core.stream.operate.NonOperation;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.storage.define.Attribute;
import org.skywalking.apm.collector.storage.define.AttributeType;
import org.skywalking.apm.collector.storage.define.DataDefine;

/**
 * @author pengys5
 */
public class NodeComponentDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 6;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(NodeComponentTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(NodeComponentTable.COLUMN_COMPONENT_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(2, new Attribute(NodeComponentTable.COLUMN_COMPONENT_NAME, AttributeType.STRING, new CoverOperation()));
        addAttribute(3, new Attribute(NodeComponentTable.COLUMN_PEER_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(4, new Attribute(NodeComponentTable.COLUMN_PEER, AttributeType.STRING, new CoverOperation()));
        addAttribute(5, new Attribute(NodeComponentTable.COLUMN_TIME_BUCKET, AttributeType.LONG, new CoverOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        Data data = build(remoteData.getDataStrings(0));
        data.setDataInteger(0, remoteData.getDataIntegers(0));
        data.setDataString(1, remoteData.getDataStrings(1));
        data.setDataInteger(1, remoteData.getDataIntegers(1));
        data.setDataString(2, remoteData.getDataStrings(2));
        data.setDataLong(0, remoteData.getDataLongs(0));
        return data;
    }

    @Override public RemoteData serialize(Object object) {
        Data data = (Data)object;
        RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(data.getDataString(0));
        builder.addDataIntegers(data.getDataInteger(0));
        builder.addDataStrings(data.getDataString(1));
        builder.addDataIntegers(data.getDataInteger(1));
        builder.addDataStrings(data.getDataString(2));
        builder.addDataLongs(data.getDataLong(0));
        return builder.build();
    }

    public static class NodeComponent implements Transform<NodeComponent> {
        private String id;
        private Integer componentId;
        private String componentName;
        private Integer peerId;
        private String peer;
        private long timeBucket;

        public NodeComponent() {
        }

        @Override public Data toData() {
            NodeComponentDataDefine define = new NodeComponentDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataInteger(0, this.componentId);
            data.setDataString(1, this.componentName);
            data.setDataInteger(1, this.peerId);
            data.setDataString(2, this.peer);
            data.setDataLong(0, this.timeBucket);
            return data;
        }

        @Override public NodeComponent toSelf(Data data) {
            this.id = data.getDataString(0);
            this.componentId = data.getDataInteger(0);
            this.componentName = data.getDataString(1);
            this.peerId = data.getDataInteger(1);
            this.peer = data.getDataString(2);
            this.timeBucket = data.getDataLong(0);
            return this;
        }

        public String getId() {
            return id;
        }

        public long getTimeBucket() {
            return timeBucket;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setTimeBucket(long timeBucket) {
            this.timeBucket = timeBucket;
        }

        public Integer getComponentId() {
            return componentId;
        }

        public void setComponentId(Integer componentId) {
            this.componentId = componentId;
        }

        public String getComponentName() {
            return componentName;
        }

        public void setComponentName(String componentName) {
            this.componentName = componentName;
        }

        public Integer getPeerId() {
            return peerId;
        }

        public void setPeerId(Integer peerId) {
            this.peerId = peerId;
        }

        public String getPeer() {
            return peer;
        }

        public void setPeer(String peer) {
            this.peer = peer;
        }
    }
}
