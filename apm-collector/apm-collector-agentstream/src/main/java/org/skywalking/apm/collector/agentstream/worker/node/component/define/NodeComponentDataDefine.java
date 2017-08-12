package org.skywalking.apm.collector.agentstream.worker.node.component.define;

import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.storage.table.node.NodeComponentTable;
import org.skywalking.apm.collector.stream.worker.impl.data.Attribute;
import org.skywalking.apm.collector.stream.worker.impl.data.AttributeType;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;
import org.skywalking.apm.collector.stream.worker.impl.data.Transform;
import org.skywalking.apm.collector.stream.worker.impl.data.operate.CoverOperation;
import org.skywalking.apm.collector.stream.worker.impl.data.operate.NonOperation;

/**
 * @author pengys5
 */
public class NodeComponentDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 3;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(NodeComponentTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(NodeComponentTable.COLUMN_AGG, AttributeType.STRING, new CoverOperation()));
        addAttribute(2, new Attribute(NodeComponentTable.COLUMN_TIME_BUCKET, AttributeType.LONG, new CoverOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        return null;
    }

    @Override public RemoteData serialize(Object object) {
        return null;
    }

    public static class NodeComponent implements Transform<NodeComponent> {
        private String id;
        private String agg;
        private long timeBucket;

        NodeComponent(String id, String agg, long timeBucket) {
            this.id = id;
            this.agg = agg;
            this.timeBucket = timeBucket;
        }

        public NodeComponent() {
        }

        @Override public Data toData() {
            NodeComponentDataDefine define = new NodeComponentDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataString(1, this.agg);
            data.setDataLong(0, this.timeBucket);
            return data;
        }

        @Override public NodeComponent toSelf(Data data) {
            this.id = data.getDataString(0);
            this.agg = data.getDataString(1);
            this.timeBucket = data.getDataLong(0);
            return this;
        }

        public String getId() {
            return id;
        }

        public String getAgg() {
            return agg;
        }

        public long getTimeBucket() {
            return timeBucket;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setAgg(String agg) {
            this.agg = agg;
        }

        public void setTimeBucket(long timeBucket) {
            this.timeBucket = timeBucket;
        }
    }
}
