package org.skywalking.apm.collector.storage.define.serviceref;

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
public class ServiceReferenceDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 15;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(ServiceReferenceTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_ID, AttributeType.INTEGER, new NonOperation()));
        addAttribute(2, new Attribute(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_NAME, AttributeType.STRING, new NonOperation()));
        addAttribute(3, new Attribute(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID, AttributeType.INTEGER, new NonOperation()));
        addAttribute(4, new Attribute(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME, AttributeType.STRING, new NonOperation()));
        addAttribute(5, new Attribute(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID, AttributeType.INTEGER, new NonOperation()));
        addAttribute(6, new Attribute(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME, AttributeType.STRING, new NonOperation()));
        addAttribute(7, new Attribute(ServiceReferenceTable.COLUMN_S1_LTE, AttributeType.LONG, new AddOperation()));
        addAttribute(8, new Attribute(ServiceReferenceTable.COLUMN_S3_LTE, AttributeType.LONG, new AddOperation()));
        addAttribute(9, new Attribute(ServiceReferenceTable.COLUMN_S5_LTE, AttributeType.LONG, new AddOperation()));
        addAttribute(10, new Attribute(ServiceReferenceTable.COLUMN_S5_GT, AttributeType.LONG, new AddOperation()));
        addAttribute(11, new Attribute(ServiceReferenceTable.COLUMN_SUMMARY, AttributeType.LONG, new AddOperation()));
        addAttribute(12, new Attribute(ServiceReferenceTable.COLUMN_ERROR, AttributeType.LONG, new AddOperation()));
        addAttribute(13, new Attribute(ServiceReferenceTable.COLUMN_COST_SUMMARY, AttributeType.LONG, new AddOperation()));
        addAttribute(14, new Attribute(ServiceReferenceTable.COLUMN_TIME_BUCKET, AttributeType.LONG, new NonOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        Data data = build(remoteData.getDataStrings(0));
        data.setDataInteger(0, remoteData.getDataIntegers(0));
        data.setDataString(1, remoteData.getDataStrings(1));
        data.setDataInteger(1, remoteData.getDataIntegers(1));
        data.setDataString(2, remoteData.getDataStrings(2));
        data.setDataInteger(2, remoteData.getDataIntegers(2));
        data.setDataString(3, remoteData.getDataStrings(3));
        data.setDataLong(0, remoteData.getDataLongs(0));
        data.setDataLong(1, remoteData.getDataLongs(1));
        data.setDataLong(2, remoteData.getDataLongs(2));
        data.setDataLong(3, remoteData.getDataLongs(3));
        data.setDataLong(4, remoteData.getDataLongs(4));
        data.setDataLong(5, remoteData.getDataLongs(5));
        data.setDataLong(6, remoteData.getDataLongs(6));
        data.setDataLong(7, remoteData.getDataLongs(7));
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
        builder.addDataIntegers(data.getDataInteger(2));
        builder.addDataStrings(data.getDataString(3));
        builder.addDataLongs(data.getDataLong(0));
        builder.addDataLongs(data.getDataLong(1));
        builder.addDataLongs(data.getDataLong(2));
        builder.addDataLongs(data.getDataLong(3));
        builder.addDataLongs(data.getDataLong(4));
        builder.addDataLongs(data.getDataLong(5));
        builder.addDataLongs(data.getDataLong(6));
        builder.addDataLongs(data.getDataLong(7));
        return builder.build();
    }

    public static class ServiceReference implements Transform {
        private String id;
        private int entryServiceId;
        private String entryServiceName;
        private int frontServiceId;
        private String frontServiceName;
        private int behindServiceId;
        private String behindServiceName;
        private long s1Lte;
        private long s3Lte;
        private long s5Lte;
        private long s5Gt;
        private long summary;
        private long error;
        private long costSummary;
        private long timeBucket;

        public ServiceReference() {
        }

        @Override public Data toData() {
            ServiceReferenceDataDefine define = new ServiceReferenceDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataInteger(0, this.entryServiceId);
            data.setDataString(1, this.entryServiceName);
            data.setDataInteger(1, this.frontServiceId);
            data.setDataString(2, this.frontServiceName);
            data.setDataInteger(2, this.behindServiceId);
            data.setDataString(3, this.behindServiceName);
            data.setDataLong(0, this.s1Lte);
            data.setDataLong(1, this.s3Lte);
            data.setDataLong(2, this.s5Lte);
            data.setDataLong(3, this.s5Gt);
            data.setDataLong(4, this.summary);
            data.setDataLong(5, this.error);
            data.setDataLong(6, this.costSummary);
            data.setDataLong(7, this.timeBucket);
            return data;
        }

        @Override public Object toSelf(Data data) {
            this.id = data.getDataString(0);
            this.entryServiceId = data.getDataInteger(0);
            this.entryServiceName = data.getDataString(1);
            this.frontServiceId = data.getDataInteger(1);
            this.frontServiceName = data.getDataString(2);
            this.behindServiceId = data.getDataInteger(2);
            this.behindServiceName = data.getDataString(3);
            this.s1Lte = data.getDataLong(0);
            this.s3Lte = data.getDataLong(1);
            this.s5Lte = data.getDataLong(2);
            this.s5Gt = data.getDataLong(3);
            this.summary = data.getDataLong(4);
            this.error = data.getDataLong(5);
            this.costSummary = data.getDataLong(6);
            this.timeBucket = data.getDataLong(7);
            return this;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public int getEntryServiceId() {
            return entryServiceId;
        }

        public void setEntryServiceId(int entryServiceId) {
            this.entryServiceId = entryServiceId;
        }

        public String getEntryServiceName() {
            return entryServiceName;
        }

        public void setEntryServiceName(String entryServiceName) {
            this.entryServiceName = entryServiceName;
        }

        public int getFrontServiceId() {
            return frontServiceId;
        }

        public void setFrontServiceId(int frontServiceId) {
            this.frontServiceId = frontServiceId;
        }

        public String getFrontServiceName() {
            return frontServiceName;
        }

        public void setFrontServiceName(String frontServiceName) {
            this.frontServiceName = frontServiceName;
        }

        public int getBehindServiceId() {
            return behindServiceId;
        }

        public void setBehindServiceId(int behindServiceId) {
            this.behindServiceId = behindServiceId;
        }

        public String getBehindServiceName() {
            return behindServiceName;
        }

        public void setBehindServiceName(String behindServiceName) {
            this.behindServiceName = behindServiceName;
        }

        public long getS1Lte() {
            return s1Lte;
        }

        public void setS1Lte(long s1Lte) {
            this.s1Lte = s1Lte;
        }

        public long getS3Lte() {
            return s3Lte;
        }

        public void setS3Lte(long s3Lte) {
            this.s3Lte = s3Lte;
        }

        public long getS5Lte() {
            return s5Lte;
        }

        public void setS5Lte(long s5Lte) {
            this.s5Lte = s5Lte;
        }

        public long getS5Gt() {
            return s5Gt;
        }

        public void setS5Gt(long s5Gt) {
            this.s5Gt = s5Gt;
        }

        public long getSummary() {
            return summary;
        }

        public void setSummary(long summary) {
            this.summary = summary;
        }

        public long getError() {
            return error;
        }

        public void setError(long error) {
            this.error = error;
        }

        public long getCostSummary() {
            return costSummary;
        }

        public void setCostSummary(long costSummary) {
            this.costSummary = costSummary;
        }

        public long getTimeBucket() {
            return timeBucket;
        }

        public void setTimeBucket(long timeBucket) {
            this.timeBucket = timeBucket;
        }
    }
}
