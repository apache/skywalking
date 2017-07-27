package org.skywalking.apm.collector.agentstream.worker.noderef.reference.define;

import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.stream.worker.impl.data.Attribute;
import org.skywalking.apm.collector.stream.worker.impl.data.AttributeType;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;
import org.skywalking.apm.collector.stream.worker.impl.data.operate.CoverOperation;
import org.skywalking.apm.collector.stream.worker.impl.data.operate.NonOperation;

/**
 * @author pengys5
 */
public class NodeRefDataDefine extends DataDefine {

    public static final int DEFINE_ID = 201;

    @Override public int defineId() {
        return DEFINE_ID;
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

    public static class NodeReference {
        private String id;
        private String agg;
        private long timeBucket;

        public NodeReference(String id, String agg, long timeBucket) {
            this.id = id;
            this.agg = agg;
            this.timeBucket = timeBucket;
        }

        public NodeReference() {
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
