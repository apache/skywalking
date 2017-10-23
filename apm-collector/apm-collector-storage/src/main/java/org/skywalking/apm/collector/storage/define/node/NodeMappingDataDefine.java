/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

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
 * @author peng-yongsheng
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
        Data data = build(remoteData.getDataStrings(0));
        data.setDataInteger(0, remoteData.getDataIntegers(0));
        data.setDataInteger(1, remoteData.getDataIntegers(1));
        data.setDataString(1, remoteData.getDataStrings(1));
        data.setDataLong(0, remoteData.getDataLongs(0));
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
        return builder.build();
    }

    public static class NodeMapping implements Transform<NodeMapping> {
        private String id;
        private int applicationId;
        private int addressId;
        private String address;
        private long timeBucket;

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
