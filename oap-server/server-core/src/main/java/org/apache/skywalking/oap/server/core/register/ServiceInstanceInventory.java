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

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.register.worker.InventoryStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.QueryUnifiedIndex;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;

import static java.util.Objects.nonNull;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE_INSTANCE_INVENTORY;

@ScopeDeclaration(id = SERVICE_INSTANCE_INVENTORY, name = "ServiceInstanceInventory")
@Stream(name = ServiceInstanceInventory.INDEX_NAME, scopeId = DefaultScopeDefine.SERVICE_INSTANCE_INVENTORY, builder = ServiceInstanceInventory.Builder.class, processor = InventoryStreamProcessor.class)
public class ServiceInstanceInventory extends RegisterSource {

    public static final String INDEX_NAME = "service_instance_inventory";

    public static final String NAME = "name";
    public static final String INSTANCE_UUID = "instance_uuid";
    public static final String SERVICE_ID = "service_id";
    public static final String IS_ADDRESS = "is_address";
    private static final String ADDRESS_ID = "address_id";
    public static final String NODE_TYPE = "node_type";
    public static final String MAPPING_SERVICE_INSTANCE_ID = "mapping_service_instance_id";
    public static final String PROPERTIES = "properties";
    private static final Gson GSON = new Gson();

    @Setter
    @Getter
    @Column(columnName = INSTANCE_UUID, matchQuery = true)
    private String instanceUUID = Const.EMPTY_STRING;
    @Setter
    @Getter
    @Column(columnName = NAME)
    @QueryUnifiedIndex(withColumns = {
        HEARTBEAT_TIME,
        REGISTER_TIME
    })
    private String name = Const.EMPTY_STRING;
    @Setter
    @Getter
    @Column(columnName = SERVICE_ID)
    private int serviceId;
    @Setter
    @Getter
    @Column(columnName = IS_ADDRESS)
    private int isAddress;
    @Setter
    @Getter
    @Column(columnName = ADDRESS_ID)
    private int addressId;
    @Setter(AccessLevel.PRIVATE)
    @Getter(AccessLevel.PACKAGE)
    @Column(columnName = NODE_TYPE)
    private int nodeType;
    @Setter
    @Getter
    @Column(columnName = MAPPING_SERVICE_INSTANCE_ID)
    private int mappingServiceInstanceId;
    @Getter(AccessLevel.PRIVATE)
    @Column(columnName = PROPERTIES)
    private String prop;
    @Getter
    private JsonObject properties;

    @Setter
    @Getter
    private boolean resetServiceInstanceMapping = false;

    @Getter
    private String language;

    public static String buildId(int serviceId, String uuid) {
        return serviceId + Const.ID_SPLIT + uuid + Const.ID_SPLIT + BooleanUtils.FALSE + Const.ID_SPLIT + Const.NONE;
    }

    public static String buildId(int serviceId, int addressId) {
        return serviceId + Const.ID_SPLIT + BooleanUtils.TRUE + Const.ID_SPLIT + addressId;
    }

    public NodeType getServiceInstanceNodeType() {
        return NodeType.get(nodeType);
    }

    public void setServiceInstanceNodeType(NodeType nodeType) {
        this.nodeType = nodeType.value();
    }

    @Override
    public String id() {
        if (BooleanUtils.TRUE == isAddress) {
            return buildId(serviceId, addressId);
        } else {
            return buildId(serviceId, instanceUUID);
        }
    }

    @Override
    public int hashCode() {
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
        setLanguage(properties);
    }

    private void setProp(String prop) {
        this.prop = prop;
        if (!Strings.isNullOrEmpty(prop)) {
            this.properties = GSON.fromJson(prop, JsonObject.class);
        }
        setLanguage(properties);
    }

    private void setLanguage(JsonObject properties) {
        if (nonNull(properties)) {
            for (String key : properties.keySet()) {
                if (key.equals(ServiceInstanceInventory.PropertyUtil.LANGUAGE)) {
                    language = properties.get(key).getAsString();
                    return;
                }
            }
        }
        language = Const.UNKNOWN;
    }

    public boolean hasProperties() {
        return prop != null && prop.length() > 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        ServiceInstanceInventory source = (ServiceInstanceInventory) obj;
        if (serviceId != source.getServiceId())
            return false;
        if (!instanceUUID.equals(source.getInstanceUUID()))
            return false;
        if (isAddress != source.getIsAddress())
            return false;
        return addressId == source.getAddressId();
    }

    public ServiceInstanceInventory getClone() {
        ServiceInstanceInventory inventory = new ServiceInstanceInventory();
        inventory.setInstanceUUID(instanceUUID);
        inventory.setName(name);
        inventory.setServiceId(serviceId);
        inventory.setIsAddress(isAddress);
        inventory.setAddressId(addressId);
        inventory.setNodeType(nodeType);
        inventory.setMappingServiceInstanceId(mappingServiceInstanceId);
        inventory.setProp(prop);
        inventory.setResetServiceInstanceMapping(resetServiceInstanceMapping);

        inventory.setSequence(getSequence());
        inventory.setRegisterTime(getRegisterTime());
        inventory.setHeartbeatTime(getHeartbeatTime());
        inventory.setLastUpdateTime(getLastUpdateTime());

        return inventory;
    }

    @Override
    public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.addDataIntegers(getSequence());
        remoteBuilder.addDataIntegers(serviceId);
        remoteBuilder.addDataIntegers(isAddress);
        remoteBuilder.addDataIntegers(addressId);
        remoteBuilder.addDataIntegers(nodeType);
        remoteBuilder.addDataIntegers(mappingServiceInstanceId);
        remoteBuilder.addDataIntegers(resetServiceInstanceMapping ? 1 : 0);

        remoteBuilder.addDataLongs(getRegisterTime());
        remoteBuilder.addDataLongs(getHeartbeatTime());
        remoteBuilder.addDataLongs(getLastUpdateTime());

        remoteBuilder.addDataStrings(Strings.isNullOrEmpty(name) ? Const.EMPTY_STRING : name);
        remoteBuilder.addDataStrings(Strings.isNullOrEmpty(instanceUUID) ? Const.EMPTY_STRING : instanceUUID);
        remoteBuilder.addDataStrings(Strings.isNullOrEmpty(prop) ? Const.EMPTY_STRING : prop);

        return remoteBuilder;
    }

    @Override
    public void deserialize(RemoteData remoteData) {
        setSequence(remoteData.getDataIntegers(0));
        setServiceId(remoteData.getDataIntegers(1));
        setIsAddress(remoteData.getDataIntegers(2));
        setAddressId(remoteData.getDataIntegers(3));
        setNodeType(remoteData.getDataIntegers(4));
        setMappingServiceInstanceId(remoteData.getDataIntegers(5));
        setResetServiceInstanceMapping(remoteData.getDataIntegers(6) == 1);

        setRegisterTime(remoteData.getDataLongs(0));
        setHeartbeatTime(remoteData.getDataLongs(1));
        setLastUpdateTime(remoteData.getDataLongs(2));

        setName(remoteData.getDataStrings(0));
        setInstanceUUID(remoteData.getDataStrings(1));
        setProp(remoteData.getDataStrings(2));
    }

    @Override
    public int remoteHashCode() {
        return 0;
    }

    @Override
    public boolean combine(RegisterSource registerSource) {
        boolean isChanged = super.combine(registerSource);
        ServiceInstanceInventory instanceInventory = (ServiceInstanceInventory) registerSource;

        if (instanceInventory.getLastUpdateTime() >= this.getLastUpdateTime()) {
            this.nodeType = instanceInventory.getNodeType();
            this.resetServiceInstanceMapping = instanceInventory.isResetServiceInstanceMapping();
            setProp(instanceInventory.getProp());
            if (instanceInventory.isResetServiceInstanceMapping()) {
                this.mappingServiceInstanceId = Const.NONE;
            } else if (Const.NONE != instanceInventory.getMappingServiceInstanceId()) {
                this.mappingServiceInstanceId = instanceInventory.mappingServiceInstanceId;
            }
            isChanged = true;
        }

        return isChanged;
    }

    public static class Builder implements StorageBuilder<ServiceInstanceInventory> {

        @Override
        public ServiceInstanceInventory map2Data(Map<String, Object> dbMap) {
            ServiceInstanceInventory inventory = new ServiceInstanceInventory();
            inventory.setSequence(((Number) dbMap.get(SEQUENCE)).intValue());
            inventory.setServiceId(((Number) dbMap.get(SERVICE_ID)).intValue());
            inventory.setIsAddress(((Number) dbMap.get(IS_ADDRESS)).intValue());
            inventory.setAddressId(((Number) dbMap.get(ADDRESS_ID)).intValue());

            inventory.setRegisterTime(((Number) dbMap.get(REGISTER_TIME)).longValue());
            inventory.setHeartbeatTime(((Number) dbMap.get(HEARTBEAT_TIME)).longValue());
            inventory.setLastUpdateTime(((Number) dbMap.get(LAST_UPDATE_TIME)).longValue());

            inventory.setNodeType(((Number) dbMap.get(NODE_TYPE)).intValue());
            inventory.setMappingServiceInstanceId(((Number) dbMap.get(MAPPING_SERVICE_INSTANCE_ID)).intValue());

            inventory.setName((String) dbMap.get(NAME));
            inventory.setInstanceUUID((String) dbMap.get(INSTANCE_UUID));
            inventory.setProp((String) dbMap.get(PROPERTIES));
            return inventory;
        }

        @Override
        public Map<String, Object> data2Map(ServiceInstanceInventory storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put(SEQUENCE, storageData.getSequence());
            map.put(SERVICE_ID, storageData.getServiceId());
            map.put(IS_ADDRESS, storageData.getIsAddress());
            map.put(ADDRESS_ID, storageData.getAddressId());

            map.put(REGISTER_TIME, storageData.getRegisterTime());
            map.put(HEARTBEAT_TIME, storageData.getHeartbeatTime());
            map.put(LAST_UPDATE_TIME, storageData.getLastUpdateTime());

            map.put(NODE_TYPE, storageData.getNodeType());
            map.put(MAPPING_SERVICE_INSTANCE_ID, storageData.getMappingServiceInstanceId());

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
