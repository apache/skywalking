package org.skywalking.apm.collector.storage.define.service;

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
public class ServiceEntryDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 5;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(ServiceEntryTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(ServiceEntryTable.COLUMN_APPLICATION_ID, AttributeType.INTEGER, new NonOperation()));
        addAttribute(2, new Attribute(ServiceEntryTable.COLUMN_ENTRY_SERVICE_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(3, new Attribute(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME, AttributeType.STRING, new CoverOperation()));
        addAttribute(4, new Attribute(ServiceEntryTable.COLUMN_TIME_BUCKET, AttributeType.LONG, new CoverOperation()));
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
        private int entryServiceId;
        private String entryServiceName;
        private long timeBucket;

        public ServiceEntry(String id, int applicationId, int entryServiceId, String entryServiceName,
            long timeBucket) {
            this.id = id;
            this.applicationId = applicationId;
            this.entryServiceId = entryServiceId;
            this.entryServiceName = entryServiceName;
            this.timeBucket = timeBucket;
        }

        public ServiceEntry() {
        }

        @Override public Data toData() {
            ServiceEntryDataDefine define = new ServiceEntryDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataInteger(0, this.applicationId);
            data.setDataInteger(1, this.entryServiceId);
            data.setDataString(1, this.entryServiceName);
            data.setDataLong(0, this.timeBucket);
            return data;
        }

        @Override public ServiceEntry toSelf(Data data) {
            this.id = data.getDataString(0);
            this.applicationId = data.getDataInteger(0);
            this.entryServiceId = data.getDataInteger(1);
            this.entryServiceName = data.getDataString(1);
            this.timeBucket = data.getDataLong(0);
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

        public long getTimeBucket() {
            return timeBucket;
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
