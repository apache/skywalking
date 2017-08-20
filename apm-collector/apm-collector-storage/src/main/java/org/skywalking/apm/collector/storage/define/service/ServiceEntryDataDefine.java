package org.skywalking.apm.collector.storage.define.service;

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
public class ServiceEntryDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 4;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(ServiceEntryTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(ServiceEntryTable.COLUMN_APPLICATION_ID, AttributeType.INTEGER, new NonOperation()));
        addAttribute(2, new Attribute(ServiceEntryTable.COLUMN_AGG, AttributeType.STRING, new CoverOperation()));
        addAttribute(3, new Attribute(ServiceEntryTable.COLUMN_TIME_BUCKET, AttributeType.LONG, new CoverOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        return null;
    }

    @Override public RemoteData serialize(Object object) {
        return null;
    }

    public static class ServiceEntry implements Transform<ServiceEntry> {
        private String id;
        private int applicationId;
        private String agg;
        private long timeBucket;

        ServiceEntry(String id, int applicationId, String agg, long timeBucket) {
            this.id = id;
            this.applicationId = applicationId;
            this.agg = agg;
            this.timeBucket = timeBucket;
        }

        public ServiceEntry() {
        }

        @Override public Data toData() {
            ServiceEntryDataDefine define = new ServiceEntryDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataInteger(0, this.applicationId);
            data.setDataString(1, this.agg);
            data.setDataLong(0, this.timeBucket);
            return data;
        }

        @Override public ServiceEntry toSelf(Data data) {
            this.id = data.getDataString(0);
            this.applicationId = data.getDataInteger(0);
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

        public int getApplicationId() {
            return applicationId;
        }

        public void setApplicationId(int applicationId) {
            this.applicationId = applicationId;
        }
    }
}
