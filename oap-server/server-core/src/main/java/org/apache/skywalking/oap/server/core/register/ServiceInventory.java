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
@InventoryType(scope = Scope.Service)
@StreamData
@StorageEntity(name = ServiceInventory.MODEL_NAME, builder = ServiceInventory.Builder.class)
public class ServiceInventory extends RegisterSource {

    public static final String MODEL_NAME = "service_inventory";

    public static final String NAME = "name";
    public static final String IS_ADDRESS = "is_address";
    private static final String ADDRESS_ID = "address_id";

    @Setter @Getter @Column(columnName = NAME, matchQuery = true) private String name = Const.EMPTY_STRING;
    @Setter @Getter @Column(columnName = IS_ADDRESS) private int isAddress;
    @Setter @Getter @Column(columnName = ADDRESS_ID) private int addressId;

    public static String buildId(String serviceName) {
        return serviceName + Const.ID_SPLIT + BooleanUtils.FALSE + Const.ID_SPLIT + Const.NONE;
    }

    public static String buildId(int addressId) {
        return BooleanUtils.TRUE + Const.ID_SPLIT + addressId;
    }

    @Override public String id() {
        if (BooleanUtils.TRUE == isAddress) {
            return buildId(addressId);
        } else {
            return buildId(name);
        }
    }

    @Override public int hashCode() {
        int result = 17;
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

        ServiceInventory source = (ServiceInventory)obj;
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
        remoteBuilder.setDataIntegers(1, isAddress);
        remoteBuilder.setDataIntegers(2, addressId);

        remoteBuilder.setDataLongs(0, getRegisterTime());
        remoteBuilder.setDataLongs(1, getHeartbeatTime());

        remoteBuilder.setDataStrings(0, name);
        return remoteBuilder;
    }

    @Override public void deserialize(RemoteData remoteData) {
        setSequence(remoteData.getDataIntegers(0));
        setIsAddress(remoteData.getDataIntegers(1));
        setAddressId(remoteData.getDataIntegers(2));

        setRegisterTime(remoteData.getDataLongs(0));
        setHeartbeatTime(remoteData.getDataLongs(1));

        setName(remoteData.getDataStrings(0));
    }

    @Override public int remoteHashCode() {
        return 0;
    }

    public static class Builder implements StorageBuilder<ServiceInventory> {

        @Override public ServiceInventory map2Data(Map<String, Object> dbMap) {
            ServiceInventory inventory = new ServiceInventory();
            inventory.setSequence((Integer)dbMap.get(SEQUENCE));
            inventory.setIsAddress((Integer)dbMap.get(IS_ADDRESS));
            inventory.setName((String)dbMap.get(NAME));
            inventory.setAddressId((Integer)dbMap.get(ADDRESS_ID));
            inventory.setRegisterTime((Long)dbMap.get(REGISTER_TIME));
            inventory.setHeartbeatTime((Long)dbMap.get(HEARTBEAT_TIME));
            return inventory;
        }

        @Override public Map<String, Object> data2Map(ServiceInventory storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put(SEQUENCE, storageData.getSequence());
            map.put(IS_ADDRESS, storageData.getIsAddress());
            map.put(NAME, storageData.getName());
            map.put(ADDRESS_ID, storageData.getAddressId());
            map.put(REGISTER_TIME, storageData.getRegisterTime());
            map.put(HEARTBEAT_TIME, storageData.getHeartbeatTime());
            return map;
        }
    }
}
