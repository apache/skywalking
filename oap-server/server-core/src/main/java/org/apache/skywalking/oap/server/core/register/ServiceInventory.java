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
import java.util.*;
import lombok.*;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.register.worker.InventoryStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.source.*;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.elasticsearch.common.Strings;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE_INVENTORY;

/**
 * @author peng-yongsheng
 */
@ScopeDeclaration(id = SERVICE_INVENTORY, name = "ServiceInventory")
@Stream(name = ServiceInventory.INDEX_NAME, scopeId = DefaultScopeDefine.SERVICE_INVENTORY, builder = ServiceInventory.Builder.class, processor = InventoryStreamProcessor.class)
public class ServiceInventory extends RegisterSource {

    public static final String INDEX_NAME = "service_inventory";

    public static final String NAME = "name";
    public static final String IS_ADDRESS = "is_address";
    private static final String ADDRESS_ID = "address_id";
    public static final String NODE_TYPE = "node_type";
    public static final String MAPPING_SERVICE_ID = "mapping_service_id";
    public static final String PROPERTIES = "properties";
    private static final Gson GSON = new Gson();

    @Setter @Getter @Column(columnName = NAME, matchQuery = true) private String name = Const.EMPTY_STRING;
    @Setter @Getter @Column(columnName = IS_ADDRESS) private int isAddress;
    @Setter @Getter @Column(columnName = ADDRESS_ID) private int addressId;
    @Setter(AccessLevel.PRIVATE) @Getter(AccessLevel.PRIVATE) @Column(columnName = NODE_TYPE) private int nodeType;
    @Setter @Getter @Column(columnName = MAPPING_SERVICE_ID) private int mappingServiceId;
    @Getter(AccessLevel.PRIVATE) @Column(columnName = PROPERTIES) private String prop = Const.EMPTY_JSON_OBJECT_STRING;
    @Getter private JsonObject properties;

    @Getter @Setter private boolean resetServiceMapping = false;

    public NodeType getServiceNodeType() {
        return NodeType.get(this.nodeType);
    }

    public static String buildId(String serviceName) {
        return serviceName + Const.ID_SPLIT + BooleanUtils.FALSE + Const.ID_SPLIT + Const.NONE;
    }

    public static String buildId(int addressId) {
        return BooleanUtils.TRUE + Const.ID_SPLIT + addressId;
    }

    public void setServiceNodeType(NodeType nodeType) {
        this.nodeType = nodeType.value();
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

    public ServiceInventory getClone() {
        ServiceInventory inventory = new ServiceInventory();
        inventory.setSequence(getSequence());
        inventory.setRegisterTime(getRegisterTime());
        inventory.setHeartbeatTime(getHeartbeatTime());
        inventory.setName(name);
        inventory.setIsAddress(isAddress);
        inventory.setNodeType(nodeType);
        inventory.setAddressId(addressId);
        inventory.setLastUpdateTime(getLastUpdateTime());
        inventory.setMappingServiceId(mappingServiceId);
        inventory.setResetServiceMapping(resetServiceMapping);
        inventory.setProp(prop);

        return inventory;
    }

    @Override public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        ServiceInventory source = (ServiceInventory)obj;
        if (!name.equals(source.getName()))
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
        remoteBuilder.addDataIntegers(isAddress);
        remoteBuilder.addDataIntegers(addressId);
        remoteBuilder.addDataIntegers(mappingServiceId);
        remoteBuilder.addDataIntegers(nodeType);
        remoteBuilder.addDataIntegers(resetServiceMapping ? 1 : 0);

        remoteBuilder.addDataLongs(getRegisterTime());
        remoteBuilder.addDataLongs(getHeartbeatTime());
        remoteBuilder.addDataLongs(getLastUpdateTime());

        remoteBuilder.addDataStrings(Strings.isNullOrEmpty(name) ? Const.EMPTY_STRING : name);
        remoteBuilder.addDataStrings(Strings.isNullOrEmpty(prop) ? Const.EMPTY_STRING : prop);
        return remoteBuilder;
    }

    @Override public void deserialize(RemoteData remoteData) {
        setSequence(remoteData.getDataIntegers(0));
        setIsAddress(remoteData.getDataIntegers(1));
        setAddressId(remoteData.getDataIntegers(2));
        setMappingServiceId(remoteData.getDataIntegers(3));
        setNodeType(remoteData.getDataIntegers(4));
        setResetServiceMapping(remoteData.getDataIntegers(5) == 1);

        setRegisterTime(remoteData.getDataLongs(0));
        setHeartbeatTime(remoteData.getDataLongs(1));
        setLastUpdateTime(remoteData.getDataLongs(2));

        setName(remoteData.getDataStrings(0));
        setProp(remoteData.getDataStrings(1));

    }

    @Override public int remoteHashCode() {
        return 0;
    }

    @Override public boolean combine(RegisterSource registerSource) {
        boolean isChanged = super.combine(registerSource);
        ServiceInventory serviceInventory = (ServiceInventory)registerSource;

        if (serviceInventory.getLastUpdateTime() >= this.getLastUpdateTime()) {
            this.nodeType = serviceInventory.getNodeType();
            this.resetServiceMapping = serviceInventory.isResetServiceMapping();
            setProp(serviceInventory.getProp());
            if (serviceInventory.isResetServiceMapping()) {
                this.mappingServiceId = Const.NONE;
            } else if (Const.NONE != serviceInventory.getMappingServiceId()) {
                this.mappingServiceId = serviceInventory.getMappingServiceId();
            }
            isChanged = true;
        }

        return isChanged;
    }

    public static class PropertyUtil {
        public static final String DATABASE = "database";
    }

    public static class Builder implements StorageBuilder<ServiceInventory> {

        @Override public ServiceInventory map2Data(Map<String, Object> dbMap) {
            ServiceInventory inventory = new ServiceInventory();
            inventory.setSequence(((Number)dbMap.get(SEQUENCE)).intValue());
            inventory.setIsAddress(((Number)dbMap.get(IS_ADDRESS)).intValue());
            inventory.setMappingServiceId(((Number)dbMap.get(MAPPING_SERVICE_ID)).intValue());
            inventory.setName((String)dbMap.get(NAME));
            inventory.setAddressId(((Number)dbMap.get(ADDRESS_ID)).intValue());
            inventory.setNodeType(((Number)dbMap.get(NODE_TYPE)).intValue());
            inventory.setRegisterTime(((Number)dbMap.get(REGISTER_TIME)).longValue());
            inventory.setHeartbeatTime(((Number)dbMap.get(HEARTBEAT_TIME)).longValue());
            inventory.setLastUpdateTime(((Number)dbMap.get(LAST_UPDATE_TIME)).longValue());
            inventory.setProp((String)dbMap.get(PROPERTIES));
            return inventory;
        }

        @Override public Map<String, Object> data2Map(ServiceInventory storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put(SEQUENCE, storageData.getSequence());
            map.put(IS_ADDRESS, storageData.getIsAddress());
            map.put(MAPPING_SERVICE_ID, storageData.getMappingServiceId());
            map.put(NAME, storageData.getName());
            map.put(ADDRESS_ID, storageData.getAddressId());
            map.put(NODE_TYPE, storageData.getNodeType());
            map.put(REGISTER_TIME, storageData.getRegisterTime());
            map.put(HEARTBEAT_TIME, storageData.getHeartbeatTime());
            map.put(LAST_UPDATE_TIME, storageData.getLastUpdateTime());
            map.put(PROPERTIES, storageData.getProp());
            return map;
        }
    }
}
