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
        return 6;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(ServiceEntryTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(ServiceEntryTable.COLUMN_APPLICATION_ID, AttributeType.INTEGER, new NonOperation()));
        addAttribute(2, new Attribute(ServiceEntryTable.COLUMN_ENTRY_SERVICE_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(3, new Attribute(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME, AttributeType.STRING, new NonOperation()));
        addAttribute(4, new Attribute(ServiceEntryTable.COLUMN_REGISTER_TIME, AttributeType.LONG, new NonOperation()));
        addAttribute(5, new Attribute(ServiceEntryTable.COLUMN_NEWEST_TIME, AttributeType.LONG, new CoverOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        Data data = build(remoteData.getDataStrings(0));
        data.setDataInteger(0, remoteData.getDataIntegers(0));
        data.setDataInteger(1, remoteData.getDataIntegers(1));
        data.setDataString(1, remoteData.getDataStrings(1));
        data.setDataLong(0, remoteData.getDataLongs(0));
        data.setDataLong(1, remoteData.getDataLongs(1));
        return data;
    }

    @Override public RemoteData serialize(Object object) {
        Data data = (Data)object;
        RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(data.getDataString(0));
        builder.addDataIntegers(data.getDataInteger(0));
        builder.addDataIntegers(data.getDataInteger(1));
        builder.addDataStrings(data.getDataString(1));
        builder.addDataLongs(data.getDataLong(0));
        builder.addDataLongs(data.getDataLong(1));
        return builder.build();
    }

    public static class ServiceEntry implements Transform<ServiceEntry> {
        private String id;
        private int applicationId;
        private int entryServiceId;
        private String entryServiceName;
        private long registerTime;
        private long newestTime;

        public ServiceEntry(String id, int applicationId, int entryServiceId, String entryServiceName,
            long registerTime,
            long newestTime) {
            this.id = id;
            this.applicationId = applicationId;
            this.entryServiceId = entryServiceId;
            this.entryServiceName = entryServiceName;
            this.registerTime = registerTime;
            this.newestTime = newestTime;
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
            data.setDataLong(0, this.registerTime);
            data.setDataLong(1, this.newestTime);
            return data;
        }

        @Override public ServiceEntry toSelf(Data data) {
            this.id = data.getDataString(0);
            this.applicationId = data.getDataInteger(0);
            this.entryServiceId = data.getDataInteger(1);
            this.entryServiceName = data.getDataString(1);
            this.registerTime = data.getDataLong(0);
            this.newestTime = data.getDataLong(1);
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

        public int getApplicationId() {
            return applicationId;
        }

        public void setApplicationId(int applicationId) {
            this.applicationId = applicationId;
        }

        public long getRegisterTime() {
            return registerTime;
        }

        public void setRegisterTime(long registerTime) {
            this.registerTime = registerTime;
        }

        public long getNewestTime() {
            return newestTime;
        }

        public void setNewestTime(long newestTime) {
            this.newestTime = newestTime;
        }
    }
}
