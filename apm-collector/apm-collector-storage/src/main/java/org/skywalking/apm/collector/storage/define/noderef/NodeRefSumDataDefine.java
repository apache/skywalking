package org.skywalking.apm.collector.storage.define.noderef;

import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.storage.define.Attribute;
import org.skywalking.apm.collector.storage.define.AttributeType;
import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.core.stream.Transform;
import org.skywalking.apm.collector.core.stream.operate.AddOperation;
import org.skywalking.apm.collector.core.stream.operate.NonOperation;

/**
 * @author pengys5
 */
public class NodeRefSumDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 9;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(NodeRefSumTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(NodeRefSumTable.COLUMN_ONE_SECOND_LESS, AttributeType.LONG, new AddOperation()));
        addAttribute(2, new Attribute(NodeRefSumTable.COLUMN_THREE_SECOND_LESS, AttributeType.LONG, new AddOperation()));
        addAttribute(3, new Attribute(NodeRefSumTable.COLUMN_FIVE_SECOND_LESS, AttributeType.LONG, new AddOperation()));
        addAttribute(4, new Attribute(NodeRefSumTable.COLUMN_FIVE_SECOND_GREATER, AttributeType.LONG, new AddOperation()));
        addAttribute(5, new Attribute(NodeRefSumTable.COLUMN_ERROR, AttributeType.LONG, new AddOperation()));
        addAttribute(6, new Attribute(NodeRefSumTable.COLUMN_SUMMARY, AttributeType.LONG, new AddOperation()));
        addAttribute(7, new Attribute(NodeRefSumTable.COLUMN_AGG, AttributeType.STRING, new NonOperation()));
        addAttribute(8, new Attribute(NodeRefSumTable.COLUMN_TIME_BUCKET, AttributeType.LONG, new NonOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        String id = remoteData.getDataStrings(0);
        String agg = remoteData.getDataStrings(1);
        Long oneSecondLess = remoteData.getDataLongs(0);
        Long threeSecondLess = remoteData.getDataLongs(1);
        Long fiveSecondLess = remoteData.getDataLongs(2);
        Long fiveSecondGreater = remoteData.getDataLongs(3);
        Long error = remoteData.getDataLongs(4);
        Long summary = remoteData.getDataLongs(5);
        long timeBucket = remoteData.getDataLongs(6);
        return new NodeReferenceSum(id, oneSecondLess, threeSecondLess, fiveSecondLess, fiveSecondGreater, error, summary, agg, timeBucket);
    }

    @Override public RemoteData serialize(Object object) {
        NodeReferenceSum nodeReferenceSum = (NodeReferenceSum)object;
        RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(nodeReferenceSum.getId());
        builder.addDataStrings(nodeReferenceSum.getAgg());
        builder.addDataLongs(nodeReferenceSum.getOneSecondLess());
        builder.addDataLongs(nodeReferenceSum.getThreeSecondLess());
        builder.addDataLongs(nodeReferenceSum.getFiveSecondLess());
        builder.addDataLongs(nodeReferenceSum.getFiveSecondGreater());
        builder.addDataLongs(nodeReferenceSum.getError());
        builder.addDataLongs(nodeReferenceSum.getSummary());
        builder.addDataLongs(nodeReferenceSum.getTimeBucket());
        return builder.build();
    }

    public static class NodeReferenceSum implements Transform {
        private String id;
        private Long oneSecondLess = 0L;
        private Long threeSecondLess = 0L;
        private Long fiveSecondLess = 0L;
        private Long fiveSecondGreater = 0L;
        private Long error = 0L;
        private Long summary = 0L;
        private String agg;
        private long timeBucket;

        public NodeReferenceSum(String id, Long oneSecondLess, Long threeSecondLess, Long fiveSecondLess,
            Long fiveSecondGreater, Long error, Long summary, String agg, long timeBucket) {
            this.id = id;
            this.oneSecondLess = oneSecondLess;
            this.threeSecondLess = threeSecondLess;
            this.fiveSecondLess = fiveSecondLess;
            this.fiveSecondGreater = fiveSecondGreater;
            this.error = error;
            this.summary = summary;
            this.agg = agg;
            this.timeBucket = timeBucket;
        }

        public NodeReferenceSum() {
        }

        @Override public Data toData() {
            NodeRefSumDataDefine define = new NodeRefSumDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataString(1, this.agg);
            data.setDataLong(0, this.oneSecondLess);
            data.setDataLong(1, this.threeSecondLess);
            data.setDataLong(2, this.fiveSecondLess);
            data.setDataLong(3, this.fiveSecondGreater);
            data.setDataLong(4, this.error);
            data.setDataLong(5, this.summary);
            data.setDataLong(6, this.timeBucket);
            return data;
        }

        @Override public Object toSelf(Data data) {
            return null;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Long getOneSecondLess() {
            return oneSecondLess;
        }

        public void setOneSecondLess(Long oneSecondLess) {
            this.oneSecondLess = oneSecondLess;
        }

        public Long getThreeSecondLess() {
            return threeSecondLess;
        }

        public void setThreeSecondLess(Long threeSecondLess) {
            this.threeSecondLess = threeSecondLess;
        }

        public Long getFiveSecondLess() {
            return fiveSecondLess;
        }

        public void setFiveSecondLess(Long fiveSecondLess) {
            this.fiveSecondLess = fiveSecondLess;
        }

        public Long getFiveSecondGreater() {
            return fiveSecondGreater;
        }

        public void setFiveSecondGreater(Long fiveSecondGreater) {
            this.fiveSecondGreater = fiveSecondGreater;
        }

        public Long getError() {
            return error;
        }

        public void setError(Long error) {
            this.error = error;
        }

        public Long getSummary() {
            return summary;
        }

        public void setSummary(Long summary) {
            this.summary = summary;
        }

        public String getAgg() {
            return agg;
        }

        public void setAgg(String agg) {
            this.agg = agg;
        }

        public long getTimeBucket() {
            return timeBucket;
        }

        public void setTimeBucket(long timeBucket) {
            this.timeBucket = timeBucket;
        }
    }
}
