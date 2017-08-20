package org.skywalking.apm.collector.storage.define.node;

import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.storage.define.Attribute;
import org.skywalking.apm.collector.storage.define.AttributeType;
import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.core.stream.Transform;
import org.skywalking.apm.collector.core.stream.operate.CoverOperation;
import org.skywalking.apm.collector.core.stream.operate.NonOperation;

/**
 * @author pengys5
 */
public class NodeMappingDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 3;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(NodeMappingTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(NodeMappingTable.COLUMN_AGG, AttributeType.STRING, new CoverOperation()));
        addAttribute(2, new Attribute(NodeMappingTable.COLUMN_TIME_BUCKET, AttributeType.LONG, new CoverOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        return null;
    }

    @Override public RemoteData serialize(Object object) {
        return null;
    }

    public static class NodeMapping implements Transform<NodeMapping> {
        private String id;
        private String agg;
        private long timeBucket;

        NodeMapping(String id, String agg, long timeBucket) {
            this.id = id;
            this.agg = agg;
            this.timeBucket = timeBucket;
        }

        public NodeMapping() {
        }

        @Override public Data toData() {
            NodeMappingDataDefine define = new NodeMappingDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataString(1, this.agg);
            data.setDataLong(0, this.timeBucket);
            return data;
        }

        @Override public NodeMapping toSelf(Data data) {
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
