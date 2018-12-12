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

import com.google.gson.Gson;
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
    public static final String OS_NAME = "os_name";
    public static final String HOST_NAME = "host_name";
    public static final String PROCESS_NO = "process_no";
    public static final String IPV4S = "ipv4s";
    public static final String LANGUAGE = "language";

    @Setter @Getter @Column(columnName = INSTANCE_UUID, matchQuery = true)
    private String instanceUUID = Const.EMPTY_STRING;
    @Setter @Getter @Column(columnName = NAME) private String name = Const.EMPTY_STRING;
    @Setter @Getter @Column(columnName = SERVICE_ID) private int serviceId;
    @Setter @Getter @Column(columnName = LANGUAGE) private int language;
    @Setter @Getter @Column(columnName = IS_ADDRESS) private int isAddress;
    @Setter @Getter @Column(columnName = ADDRESS_ID) private int addressId;
    @Setter @Getter @Column(columnName = OS_NAME) private String osName;
    @Setter @Getter @Column(columnName = HOST_NAME) private String hostName;
    @Setter @Getter @Column(columnName = PROCESS_NO) private int processNo;
    @Setter @Getter @Column(columnName = IPV4S) private String ipv4s;

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
        remoteBuilder.addDataIntegers(language);
        remoteBuilder.addDataIntegers(isAddress);
        remoteBuilder.addDataIntegers(addressId);
        remoteBuilder.addDataIntegers(processNo);

        remoteBuilder.addDataLongs(getRegisterTime());
        remoteBuilder.addDataLongs(getHeartbeatTime());

        remoteBuilder.addDataStrings(Strings.isNullOrEmpty(name) ? Const.EMPTY_STRING : name);
        remoteBuilder.addDataStrings(Strings.isNullOrEmpty(osName) ? Const.EMPTY_STRING : osName);
        remoteBuilder.addDataStrings(Strings.isNullOrEmpty(hostName) ? Const.EMPTY_STRING : hostName);
        remoteBuilder.addDataStrings(Strings.isNullOrEmpty(ipv4s) ? Const.EMPTY_STRING : ipv4s);
        remoteBuilder.addDataStrings(Strings.isNullOrEmpty(instanceUUID) ? Const.EMPTY_STRING : instanceUUID);
        return remoteBuilder;
    }

    @Override public void deserialize(RemoteData remoteData) {
        setSequence(remoteData.getDataIntegers(0));
        setServiceId(remoteData.getDataIntegers(1));
        setLanguage(remoteData.getDataIntegers(2));
        setIsAddress(remoteData.getDataIntegers(3));
        setAddressId(remoteData.getDataIntegers(4));
        setProcessNo(remoteData.getDataIntegers(5));

        setRegisterTime(remoteData.getDataLongs(0));
        setHeartbeatTime(remoteData.getDataLongs(1));

        setName(remoteData.getDataStrings(0));
        setOsName(remoteData.getDataStrings(1));
        setHostName(remoteData.getDataStrings(2));
        setIpv4s(remoteData.getDataStrings(3));
        setInstanceUUID(remoteData.getDataStrings(4));
    }

    @Override public int remoteHashCode() {
        return 0;
    }

    public static class Builder implements StorageBuilder<ServiceInstanceInventory> {

        @Override public ServiceInstanceInventory map2Data(Map<String, Object> dbMap) {
            ServiceInstanceInventory inventory = new ServiceInstanceInventory();
            inventory.setSequence((Integer)dbMap.get(SEQUENCE));
            inventory.setServiceId((Integer)dbMap.get(SERVICE_ID));
            inventory.setLanguage((Integer)dbMap.get(LANGUAGE));
            inventory.setIsAddress((Integer)dbMap.get(IS_ADDRESS));
            inventory.setAddressId((Integer)dbMap.get(ADDRESS_ID));
            inventory.setProcessNo((Integer)dbMap.get(PROCESS_NO));

            inventory.setRegisterTime((Long)dbMap.get(REGISTER_TIME));
            inventory.setHeartbeatTime((Long)dbMap.get(HEARTBEAT_TIME));

            inventory.setName((String)dbMap.get(NAME));
            inventory.setOsName((String)dbMap.get(OS_NAME));
            inventory.setHostName((String)dbMap.get(HOST_NAME));
            inventory.setIpv4s((String)dbMap.get(IPV4S));
            inventory.setInstanceUUID((String)dbMap.get(INSTANCE_UUID));
            return inventory;
        }

        @Override public Map<String, Object> data2Map(ServiceInstanceInventory storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put(SEQUENCE, storageData.getSequence());
            map.put(SERVICE_ID, storageData.getServiceId());
            map.put(LANGUAGE, storageData.getLanguage());
            map.put(IS_ADDRESS, storageData.getIsAddress());
            map.put(ADDRESS_ID, storageData.getAddressId());
            map.put(PROCESS_NO, storageData.getProcessNo());

            map.put(REGISTER_TIME, storageData.getRegisterTime());
            map.put(HEARTBEAT_TIME, storageData.getHeartbeatTime());

            map.put(NAME, storageData.getName());
            map.put(OS_NAME, storageData.getOsName());
            map.put(HOST_NAME, storageData.getHostName());
            map.put(IPV4S, storageData.getIpv4s());
            map.put(INSTANCE_UUID, storageData.getInstanceUUID());
            return map;
        }
    }

    public static class AgentOsInfo {
        @Setter @Getter private String osName;
        @Setter @Getter private String hostname;
        @Setter @Getter private int processNo;
        @Getter private List<String> ipv4s;

        public AgentOsInfo() {
            this.ipv4s = new ArrayList<>();
        }

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
