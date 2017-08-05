package org.skywalking.apm.collector.agentstream.worker.noderef.reference.define;

import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
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
public class NodeRefDataDefine extends DataDefine {

    @Override public int defineId() {
        return 201;
    }

    @Override protected int initialCapacity() {
        return 3;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(NodeRefTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(NodeRefTable.COLUMN_AGG, AttributeType.STRING, new CoverOperation()));
        addAttribute(2, new Attribute(NodeRefTable.COLUMN_TIME_BUCKET, AttributeType.LONG, new CoverOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        String id = remoteData.getDataStrings(0);
        String agg = remoteData.getDataStrings(1);
        long timeBucket = remoteData.getDataLongs(0);
        return new NodeReference(id, agg, timeBucket);
    }

    @Override public RemoteData serialize(Object object) {
        NodeReference nodeReference = (NodeReference)object;
        RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(nodeReference.getId());
        builder.addDataStrings(nodeReference.getAgg());
        builder.addDataLongs(nodeReference.getTimeBucket());
        return builder.build();
    }

    public static class NodeReference implements Transform {
        private String id;
        private String agg;
        private long timeBucket;

        NodeReference(String id, String agg, long timeBucket) {
            this.id = id;
            this.agg = agg;
            this.timeBucket = timeBucket;
        }

        public NodeReference() {
        }

        @Override public Data toData() {
            NodeRefDataDefine define = new NodeRefDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataString(1, this.agg);
            data.setDataLong(0, this.timeBucket);
            return data;
        }

        @Override public Object toSelf(Data data) {
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
