package org.skywalking.apm.collector.agentstream.worker.noderef.summary.define;

import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.stream.worker.impl.data.Attribute;
import org.skywalking.apm.collector.stream.worker.impl.data.AttributeType;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;
import org.skywalking.apm.collector.stream.worker.impl.data.operate.CoverOperation;
import org.skywalking.apm.collector.stream.worker.impl.data.operate.NonOperation;

/**
 * @author pengys5
 */
public class NodeRefSumDataDefine extends DataDefine {

    public static final int DEFINE_ID = 202;

    @Override public int defineId() {
        return DEFINE_ID;
    }

    @Override protected int initialCapacity() {
        return 9;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(NodeRefSumTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(NodeRefSumTable.COLUMN_ONE_SECOND_LESS, AttributeType.LONG, new NonOperation()));
        addAttribute(2, new Attribute(NodeRefSumTable.COLUMN_THREE_SECOND_LESS, AttributeType.LONG, new NonOperation()));
        addAttribute(3, new Attribute(NodeRefSumTable.COLUMN_FIVE_SECOND_LESS, AttributeType.LONG, new NonOperation()));
        addAttribute(4, new Attribute(NodeRefSumTable.COLUMN_FIVE_SECOND_GREATER, AttributeType.LONG, new NonOperation()));
        addAttribute(5, new Attribute(NodeRefSumTable.COLUMN_ERROR, AttributeType.LONG, new NonOperation()));
        addAttribute(6, new Attribute(NodeRefSumTable.COLUMN_SUMMARY, AttributeType.LONG, new NonOperation()));
        addAttribute(7, new Attribute(NodeRefSumTable.COLUMN_AGG, AttributeType.STRING, new CoverOperation()));
        addAttribute(8, new Attribute(NodeRefSumTable.COLUMN_TIME_BUCKET, AttributeType.LONG, new CoverOperation()));
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

    public static class NodeReferenceSum {
        private String id;
        private Long oneSecondLess;
        private Long threeSecondLess;
        private Long fiveSecondLess;
        private Long fiveSecondGreater;
        private Long error;
        private Long summary;
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
