package org.skywalking.apm.collector.storage.define.node;

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
public class NodeMappingDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 5;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(NodeMappingTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(NodeMappingTable.COLUMN_APPLICATION_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(2, new Attribute(NodeMappingTable.COLUMN_ADDRESS_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(3, new Attribute(NodeMappingTable.COLUMN_ADDRESS, AttributeType.STRING, new CoverOperation()));
        addAttribute(4, new Attribute(NodeMappingTable.COLUMN_TIME_BUCKET, AttributeType.LONG, new CoverOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        return null;
    }

    @Override public RemoteData serialize(Object object) {
        return null;
    }

    public static class NodeMapping implements Transform<NodeMapping> {
        private String id;
        private int applicationId;
        private int addressId;
        private String address;
        private long timeBucket;

        public NodeMapping(String id, int applicationId, int addressId, String address, long timeBucket) {
            this.id = id;
            this.applicationId = applicationId;
            this.addressId = addressId;
            this.address = address;
            this.timeBucket = timeBucket;
        }

        public NodeMapping() {
        }

        @Override public Data toData() {
            NodeMappingDataDefine define = new NodeMappingDataDefine();
            Data data = define.build(id);
            data.setDataString(0, this.id);
            data.setDataInteger(0, this.applicationId);
            data.setDataInteger(1, this.addressId);
            data.setDataString(1, this.address);
            data.setDataLong(0, this.timeBucket);
            return data;
        }

        @Override public NodeMapping toSelf(Data data) {
            this.id = data.getDataString(0);
            this.applicationId = data.getDataInteger(0);
            this.addressId = data.getDataInteger(1);
            this.address = data.getDataString(1);
            this.timeBucket = data.getDataLong(0);
            return this;
        }

        public String getId() {
            return id;
        }

        public long getTimeBucket() {
            return timeBucket;
        }

        public void setId(String id) {
            this.id = id;
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

        public int getAddressId() {
            return addressId;
        }

        public void setAddressId(int addressId) {
            this.addressId = addressId;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }
    }
}
