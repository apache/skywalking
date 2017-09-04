package org.skywalking.apm.collector.storage.define.noderef;

import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.core.stream.Transform;
import org.skywalking.apm.collector.core.stream.operate.AddOperation;
import org.skywalking.apm.collector.core.stream.operate.NonOperation;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.storage.define.Attribute;
import org.skywalking.apm.collector.storage.define.AttributeType;
import org.skywalking.apm.collector.storage.define.DataDefine;

/**
 * @author pengys5
 */
public class NodeReferenceDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 11;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(NodeReferenceTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(NodeReferenceTable.COLUMN_FRONT_APPLICATION_ID, AttributeType.INTEGER, new NonOperation()));
        addAttribute(2, new Attribute(NodeReferenceTable.COLUMN_BEHIND_APPLICATION_ID, AttributeType.INTEGER, new NonOperation()));
        addAttribute(3, new Attribute(NodeReferenceTable.COLUMN_BEHIND_PEER, AttributeType.STRING, new NonOperation()));
        addAttribute(4, new Attribute(NodeReferenceTable.COLUMN_S1_LTE, AttributeType.INTEGER, new AddOperation()));
        addAttribute(5, new Attribute(NodeReferenceTable.COLUMN_S3_LTE, AttributeType.INTEGER, new AddOperation()));
        addAttribute(6, new Attribute(NodeReferenceTable.COLUMN_S5_LTE, AttributeType.INTEGER, new AddOperation()));
        addAttribute(7, new Attribute(NodeReferenceTable.COLUMN_S5_GT, AttributeType.INTEGER, new AddOperation()));
        addAttribute(8, new Attribute(NodeReferenceTable.COLUMN_SUMMARY, AttributeType.INTEGER, new AddOperation()));
        addAttribute(9, new Attribute(NodeReferenceTable.COLUMN_ERROR, AttributeType.INTEGER, new AddOperation()));
        addAttribute(10, new Attribute(NodeReferenceTable.COLUMN_TIME_BUCKET, AttributeType.LONG, new NonOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        String id = remoteData.getDataStrings(0);
        int applicationId = remoteData.getDataIntegers(0);
        int behindApplicationId = remoteData.getDataIntegers(1);
        String behindPeer = remoteData.getDataStrings(1);
        int s1LTE = remoteData.getDataIntegers(2);
        int s3LTE = remoteData.getDataIntegers(3);
        int s5LTE = remoteData.getDataIntegers(4);
        int s5GT = remoteData.getDataIntegers(5);
        int summary = remoteData.getDataIntegers(6);
        int error = remoteData.getDataIntegers(7);
        long timeBucket = remoteData.getDataLongs(0);
        return new NodeReference(id, applicationId, behindApplicationId, behindPeer, s1LTE, s3LTE, s5LTE, s5GT, summary, error, timeBucket);
    }

    @Override public RemoteData serialize(Object object) {
        NodeReference nodeReference = (NodeReference)object;
        RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(nodeReference.getId());
        builder.addDataIntegers(nodeReference.getFrontApplicationId());
        builder.addDataIntegers(nodeReference.getBehindApplicationId());
        builder.addDataStrings(nodeReference.getBehindPeer());
        builder.addDataIntegers(nodeReference.getS1LTE());
        builder.addDataIntegers(nodeReference.getS3LTE());
        builder.addDataIntegers(nodeReference.getS5LTE());
        builder.addDataIntegers(nodeReference.getS5GT());
        builder.addDataIntegers(nodeReference.getSummary());
        builder.addDataIntegers(nodeReference.getError());
        builder.addDataLongs(nodeReference.getTimeBucket());
        return builder.build();
    }

    public static class NodeReference implements Transform {
        private String id;
        private int frontApplicationId;
        private int behindApplicationId;
        private String behindPeer;
        private int s1LTE = 0;
        private int s3LTE = 0;
        private int s5LTE = 0;
        private int s5GT = 0;
        private int summary = 0;
        private int error = 0;
        private long timeBucket;

        public NodeReference(String id, int frontApplicationId, int behindApplicationId, String behindPeer, int s1LTE,
            int s3LTE,
            int s5LTE, int s5GT, int summary, int error, long timeBucket) {
            this.id = id;
            this.frontApplicationId = frontApplicationId;
            this.behindApplicationId = behindApplicationId;
            this.behindPeer = behindPeer;
            this.s1LTE = s1LTE;
            this.s3LTE = s3LTE;
            this.s5LTE = s5LTE;
            this.s5GT = s5GT;
            this.summary = summary;
            this.error = error;
            this.timeBucket = timeBucket;
        }

        public NodeReference() {
        }

        @Override public Data toData() {
            NodeReferenceDataDefine define = new NodeReferenceDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataInteger(0, this.frontApplicationId);
            data.setDataInteger(1, this.behindApplicationId);
            data.setDataString(1, this.behindPeer);
            data.setDataInteger(2, this.s1LTE);
            data.setDataInteger(3, this.s3LTE);
            data.setDataInteger(4, this.s5LTE);
            data.setDataInteger(5, this.s5GT);
            data.setDataInteger(6, this.summary);
            data.setDataInteger(7, this.error);
            data.setDataLong(0, this.timeBucket);
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

        public int getFrontApplicationId() {
            return frontApplicationId;
        }

        public void setFrontApplicationId(int frontApplicationId) {
            this.frontApplicationId = frontApplicationId;
        }

        public int getBehindApplicationId() {
            return behindApplicationId;
        }

        public void setBehindApplicationId(int behindApplicationId) {
            this.behindApplicationId = behindApplicationId;
        }

        public String getBehindPeer() {
            return behindPeer;
        }

        public void setBehindPeer(String behindPeer) {
            this.behindPeer = behindPeer;
        }

        public int getS1LTE() {
            return s1LTE;
        }

        public void setS1LTE(int s1LTE) {
            this.s1LTE = s1LTE;
        }

        public int getS3LTE() {
            return s3LTE;
        }

        public void setS3LTE(int s3LTE) {
            this.s3LTE = s3LTE;
        }

        public int getS5LTE() {
            return s5LTE;
        }

        public void setS5LTE(int s5LTE) {
            this.s5LTE = s5LTE;
        }

        public int getS5GT() {
            return s5GT;
        }

        public void setS5GT(int s5GT) {
            this.s5GT = s5GT;
        }

        public int getError() {
            return error;
        }

        public void setError(int error) {
            this.error = error;
        }

        public int getSummary() {
            return summary;
        }

        public void setSummary(int summary) {
            this.summary = summary;
        }

        public long getTimeBucket() {
            return timeBucket;
        }

        public void setTimeBucket(long timeBucket) {
            this.timeBucket = timeBucket;
        }
    }
}
