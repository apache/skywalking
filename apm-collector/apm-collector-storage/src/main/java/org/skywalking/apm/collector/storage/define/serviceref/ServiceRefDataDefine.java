package org.skywalking.apm.collector.storage.define.serviceref;

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
public class ServiceRefDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 4;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(ServiceRefTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(ServiceRefTable.COLUMN_ENTRY_SERVICE, AttributeType.STRING, new NonOperation()));
        addAttribute(2, new Attribute(ServiceRefTable.COLUMN_AGG, AttributeType.STRING, new CoverOperation()));
        addAttribute(3, new Attribute(ServiceRefTable.COLUMN_TIME_BUCKET, AttributeType.LONG, new CoverOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        String id = remoteData.getDataStrings(0);
        String entryService = remoteData.getDataStrings(1);
        String agg = remoteData.getDataStrings(2);
        long timeBucket = remoteData.getDataLongs(0);
        return new ServiceReference(id, entryService, agg, timeBucket);
    }

    @Override public RemoteData serialize(Object object) {
        ServiceReference serviceReference = (ServiceReference)object;
        RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(serviceReference.getId());
        builder.addDataStrings(serviceReference.getEntryService());
        builder.addDataStrings(serviceReference.getAgg());
        builder.addDataLongs(serviceReference.getTimeBucket());
        return builder.build();
    }

    public static class ServiceReference implements Transform {
        private String id;
        private String entryService;
        private String agg;
        private long timeBucket;

        public ServiceReference(String id, String entryService, String agg, long timeBucket) {
            this.id = id;
            this.entryService = entryService;
            this.agg = agg;
            this.timeBucket = timeBucket;
        }

        public ServiceReference() {
        }

        @Override public Data toData() {
            ServiceRefDataDefine define = new ServiceRefDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataString(1, this.entryService);
            data.setDataString(2, this.agg);
            data.setDataLong(0, this.timeBucket);
            return data;
        }

        @Override public Object toSelf(Data data) {
            this.id = data.getDataString(0);
            this.entryService = data.getDataString(1);
            this.agg = data.getDataString(2);
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

        public String getEntryService() {
            return entryService;
        }

        public void setEntryService(String entryService) {
            this.entryService = entryService;
        }
    }
}
