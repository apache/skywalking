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

/**
 * @author peng-yongsheng
 */
@InventoryType(scope = Scope.ServiceInstance)
@StreamData
@StorageEntity(name = ServiceInstanceInventory.MODEL_NAME, builder = ServiceInstanceInventory.Builder.class)
public class ServiceInstanceInventory extends RegisterSource {

    public static final String MODEL_NAME = "service_instance_inventory";

    public static final String NAME = "name";
    public static final String SERVICE_ID = "service_id";
    public static final String IS_ADDRESS = "is_address";
    public static final String ADDRESS_ID = "address_id";
    private static final String OS_NAME = "os_name";
    private static final String HOST_NAME = "host_name";
    private static final String PROCESS_NO = "process_no";
    private static final String IPV4 = "ipv4";

    @Setter @Getter @Column(columnName = NAME, matchQuery = true) private String name = Const.EMPTY_STRING;
    @Setter @Getter @Column(columnName = SERVICE_ID) private int serviceId;
    @Setter @Getter @Column(columnName = IS_ADDRESS) private int isAddress;
    @Setter @Getter @Column(columnName = ADDRESS_ID) private int addressId;
    @Setter @Getter @Column(columnName = OS_NAME) private String osName;
    @Setter @Getter @Column(columnName = HOST_NAME) private String hostName;
    @Setter @Getter @Column(columnName = PROCESS_NO) private int processNo;
    @Setter @Getter @Column(columnName = IPV4) private String ipv4;

    public static String buildId(int serviceId, String serviceInstanceName) {
        return serviceId + Const.ID_SPLIT + serviceInstanceName + Const.ID_SPLIT + BooleanUtils.FALSE + Const.ID_SPLIT + Const.NONE;
    }

    public static String buildId(int serviceId, int addressId) {
        return serviceId + Const.ID_SPLIT + BooleanUtils.TRUE + Const.ID_SPLIT + addressId;
    }

    @Override public String id() {
        if (BooleanUtils.TRUE == isAddress) {
            return buildId(serviceId, addressId);
        } else {
            return buildId(serviceId, name);
        }
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + serviceId;
        result = 31 * result + name.hashCode();
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
        if (name.equals(source.getName()))
            return false;
        if (isAddress != source.getIsAddress())
            return false;
        if (addressId != source.getAddressId())
            return false;

        return true;
    }

    @Override public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.setDataIntegers(0, getSequence());
        remoteBuilder.setDataIntegers(1, serviceId);
        remoteBuilder.setDataIntegers(2, isAddress);
        remoteBuilder.setDataIntegers(3, addressId);
        remoteBuilder.setDataIntegers(4, processNo);

        remoteBuilder.setDataLongs(0, getRegisterTime());
        remoteBuilder.setDataLongs(1, getHeartbeatTime());

        remoteBuilder.setDataStrings(0, name);
        remoteBuilder.setDataStrings(1, osName);
        remoteBuilder.setDataStrings(2, hostName);
        remoteBuilder.setDataStrings(3, ipv4);
        return remoteBuilder;
    }

    @Override public void deserialize(RemoteData remoteData) {
        setSequence(remoteData.getDataIntegers(0));
        setServiceId(remoteData.getDataIntegers(1));
        setIsAddress(remoteData.getDataIntegers(2));
        setAddressId(remoteData.getDataIntegers(3));
        setProcessNo(remoteData.getDataIntegers(4));

        setRegisterTime(remoteData.getDataLongs(0));
        setHeartbeatTime(remoteData.getDataLongs(1));

        setName(remoteData.getDataStrings(0));
        setOsName(remoteData.getDataStrings(1));
        setHostName(remoteData.getDataStrings(2));
        setIpv4(remoteData.getDataStrings(3));
    }

    public static class Builder implements StorageBuilder<ServiceInstanceInventory> {

        @Override public ServiceInstanceInventory map2Data(Map<String, Object> dbMap) {
            ServiceInstanceInventory inventory = new ServiceInstanceInventory();
            inventory.setSequence((Integer)dbMap.get(SEQUENCE));
            inventory.setServiceId((Integer)dbMap.get(SERVICE_ID));
            inventory.setIsAddress((Integer)dbMap.get(IS_ADDRESS));
            inventory.setAddressId((Integer)dbMap.get(ADDRESS_ID));
            inventory.setProcessNo((Integer)dbMap.get(PROCESS_NO));

            inventory.setRegisterTime((Long)dbMap.get(REGISTER_TIME));
            inventory.setHeartbeatTime((Long)dbMap.get(HEARTBEAT_TIME));

            inventory.setName((String)dbMap.get(NAME));
            inventory.setOsName((String)dbMap.get(OS_NAME));
            inventory.setHostName((String)dbMap.get(HOST_NAME));
            inventory.setIpv4((String)dbMap.get(IPV4));
            return inventory;
        }

        @Override public Map<String, Object> data2Map(ServiceInstanceInventory storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put(SEQUENCE, storageData.getSequence());
            map.put(SERVICE_ID, storageData.getServiceId());
            map.put(IS_ADDRESS, storageData.getIsAddress());
            map.put(ADDRESS_ID, storageData.getAddressId());
            map.put(PROCESS_NO, storageData.getProcessNo());

            map.put(REGISTER_TIME, storageData.getRegisterTime());
            map.put(HEARTBEAT_TIME, storageData.getHeartbeatTime());

            map.put(NAME, storageData.getName());
            map.put(OS_NAME, storageData.getOsName());
            map.put(HOST_NAME, storageData.getHostName());
            map.put(IPV4, storageData.getIpv4());
            return map;
        }
    }
}
