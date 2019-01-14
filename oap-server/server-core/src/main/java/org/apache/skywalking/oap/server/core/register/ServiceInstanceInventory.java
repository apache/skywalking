/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.core.register;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.util.*;
import lombok.*;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.register.annotation.InventoryType;
import org.apache.skywalking.oap.server.core.remote.annotation.StreamData;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.source.Scope;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.*;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.elasticsearch.common.Strings;

/**
 * @author peng-yongsheng
 */
@InventoryType
@StreamData
@StorageEntity(name = ServiceInstanceInventory.MODEL_NAME, builder = ServiceInstanceInventory.Builder.class, deleteHistory = false, source = Scope.ServiceInstanceInventory)
public class ServiceInstanceInventory extends RegisterSource {

    public static final String MODEL_NAME = "service_instance_inventory";

    public static final String NAME = "name";
    public static final String INSTANCE_UUID = "instance_uuid";
    public static final String SERVICE_ID = "service_id";
    private static final String IS_ADDRESS = "is_address";
    private static final String ADDRESS_ID = "address_id";
    public static final String PROPERTIES = "properties";
    private static final Gson GSON = new Gson();

    @Setter @Getter @Column(columnName = INSTANCE_UUID, matchQuery = true)
    private String instanceUUID = Const.EMPTY_STRING;
    @Setter @Getter @Column(columnName = NAME) private String name = Const.EMPTY_STRING;
    @Setter @Getter @Column(columnName = SERVICE_ID) private int serviceId;
    @Setter @Getter @Column(columnName = IS_ADDRESS) private int isAddress;
    @Setter @Getter @Column(columnName = ADDRESS_ID) private int addressId;
    @Getter(AccessLevel.PRIVATE) @Column(columnName = PROPERTIES) private String prop;
    @Getter private JsonObject properties;

    public static String buildId(int serviceId, String uuid) {
        return serviceId + Const.ID_SPLIT + uuid + Const.ID_SPLIT + BooleanUtils.FALSE + Const.ID_SPLIT + Const.NONE;
    }

    public static String buildId(int serviceId, int addressId) {
        return serviceId + Const.ID_SPLIT + BooleanUtils.TRUE + Const.ID_SPLIT + addressId;
    }

    @Override public String id() {
        if (BooleanUtils.TRUE == isAddress) {
            return buildId(serviceId, addressId);
        } else {
            return buildId(serviceId, instanceUUID);
        }
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + serviceId;
        result = 31 * result + instanceUUID.hashCode();
        result = 31 * result + isAddress;
        result = 31 * result + addressId;
        return result;
    }

    public void setProperties(JsonObject properties) {
        this.properties = properties;
        if (properties != null && properties.keySet().size() > 0) {
            this.prop = properties.toString();
        }
    }

    private void setProp(String prop) {
        this.prop = prop;
        if (!Strings.isNullOrEmpty(prop)) {
            this.properties = GSON.fromJson(prop, JsonObject.class);
        }
    }

    public boolean hasProperties() {
        return prop != null && prop.length() > 0;
    }


    @Override public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        ServiceInstanceInventory source = (ServiceInstanceInventory)obj;
        if (serviceId != source.getServiceId())
            return false;
        if (!instanceUUID.equals(source.getInstanceUUID()))
            return false;
        if (isAddress != source.getIsAddress())
            return false;
        if (addressId != source.getAddressId())
            return false;

        return true;
    }

    @Override public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.addDataIntegers(getSequence());
        remoteBuilder.addDataIntegers(serviceId);
        remoteBuilder.addDataIntegers(isAddress);
        remoteBuilder.addDataIntegers(addressId);

        remoteBuilder.addDataLongs(getRegisterTime());
        remoteBuilder.addDataLongs(getHeartbeatTime());

        remoteBuilder.addDataStrings(Strings.isNullOrEmpty(name) ? Const.EMPTY_STRING : name);
        remoteBuilder.addDataStrings(Strings.isNullOrEmpty(instanceUUID) ? Const.EMPTY_STRING : instanceUUID);
        remoteBuilder.addDataStrings(Strings.isNullOrEmpty(prop) ? Const.EMPTY_STRING : prop);
        return remoteBuilder;
    }

    @Override public void deserialize(RemoteData remoteData) {
        setSequence(remoteData.getDataIntegers(0));
        setServiceId(remoteData.getDataIntegers(1));
        setIsAddress(remoteData.getDataIntegers(2));
        setAddressId(remoteData.getDataIntegers(3));

        setRegisterTime(remoteData.getDataLongs(0));
        setHeartbeatTime(remoteData.getDataLongs(1));

        setName(remoteData.getDataStrings(0));
        setInstanceUUID(remoteData.getDataStrings(1));
        setProp(remoteData.getDataStrings(2));
    }

    @Override public int remoteHashCode() {
        return 0;
    }

    public static class Builder implements StorageBuilder<ServiceInstanceInventory> {

        @Override public ServiceInstanceInventory map2Data(Map<String, Object> dbMap) {
            ServiceInstanceInventory inventory = new ServiceInstanceInventory();
            inventory.setSequence((Integer)dbMap.get(SEQUENCE));
            inventory.setServiceId((Integer)dbMap.get(SERVICE_ID));
            inventory.setIsAddress((Integer)dbMap.get(IS_ADDRESS));
            inventory.setAddressId((Integer)dbMap.get(ADDRESS_ID));

            inventory.setRegisterTime((Long)dbMap.get(REGISTER_TIME));
            inventory.setHeartbeatTime((Long)dbMap.get(HEARTBEAT_TIME));

            inventory.setName((String)dbMap.get(NAME));
            inventory.setInstanceUUID((String)dbMap.get(INSTANCE_UUID));
            inventory.setProp((String)dbMap.get(PROPERTIES));
            return inventory;
        }

        @Override public Map<String, Object> data2Map(ServiceInstanceInventory storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put(SEQUENCE, storageData.getSequence());
            map.put(SERVICE_ID, storageData.getServiceId());
            map.put(IS_ADDRESS, storageData.getIsAddress());
            map.put(ADDRESS_ID, storageData.getAddressId());

            map.put(REGISTER_TIME, storageData.getRegisterTime());
            map.put(HEARTBEAT_TIME, storageData.getHeartbeatTime());

            map.put(NAME, storageData.getName());
            map.put(INSTANCE_UUID, storageData.getInstanceUUID());
            map.put(PROPERTIES, storageData.getProp());
            return map;
        }
    }

    public static class PropertyUtil {
        public static final String OS_NAME = "os_name";
        public static final String HOST_NAME = "host_name";
        public static final String PROCESS_NO = "process_no";
        public static final String IPV4S = "ipv4s";
        public static final String LANGUAGE = "language";

        public static String ipv4sSerialize(List<String> ipv4) {
            Gson gson = new Gson();
            return gson.toJson(ipv4);
        }

        public static List<String> ipv4sDeserialize(String ipv4s) {
            Gson gson = new Gson();
            return gson.fromJson(ipv4s, new TypeToken<List<String>>() {
            }.getType());
        }
    }
}
