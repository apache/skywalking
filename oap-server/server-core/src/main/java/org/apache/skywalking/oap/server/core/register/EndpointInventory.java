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

/**
 * @author peng-yongsheng
 */
@InventoryType(scope = Scope.Endpoint)
@StreamData
@StorageEntity(name = EndpointInventory.MODEL_NAME, builder = EndpointInventory.Builder.class)
public class EndpointInventory extends RegisterSource {

    public static final String MODEL_NAME = "endpoint_inventory";

    private static final String SERVICE_ID = "service_id";
    private static final String NAME = "name";
    private static final String SRC_SPAN_TYPE = "src_span_type";

    @Setter @Getter @Column(columnName = SERVICE_ID) private int serviceId;
    @Setter @Getter @Column(columnName = NAME, matchQuery = true) private String name = Const.EMPTY_STRING;
    @Setter @Getter @Column(columnName = SRC_SPAN_TYPE) private int srcSpanType;

    public static String buildId(int serviceId, String endpointName) {
        return serviceId + Const.ID_SPLIT + endpointName;
    }

    @Override public String id() {
        return buildId(serviceId, name);
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + serviceId;
        result = 31 * result + name.hashCode();
        return result;
    }

    @Override public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        EndpointInventory source = (EndpointInventory)obj;
        if (serviceId != source.getServiceId())
            return false;
        if (name.equals(source.getName()))
            return false;

        return true;
    }

    @Override public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.setDataIntegers(0, getSequence());
        remoteBuilder.setDataIntegers(1, serviceId);
        remoteBuilder.setDataIntegers(2, srcSpanType);

        remoteBuilder.setDataLongs(0, getRegisterTime());
        remoteBuilder.setDataLongs(1, getHeartbeatTime());

        remoteBuilder.setDataStrings(0, name);
        return remoteBuilder;
    }

    @Override public void deserialize(RemoteData remoteData) {
        setSequence(remoteData.getDataIntegers(0));
        setServiceId(remoteData.getDataIntegers(1));
        setSrcSpanType(remoteData.getDataIntegers(2));

        setRegisterTime(remoteData.getDataLongs(0));
        setHeartbeatTime(remoteData.getDataLongs(1));

        setName(remoteData.getDataStrings(0));
    }

    public static class Builder implements StorageBuilder<EndpointInventory> {

        @Override public EndpointInventory map2Data(Map<String, Object> dbMap) {
            EndpointInventory inventory = new EndpointInventory();
            inventory.setSequence((Integer)dbMap.get(SEQUENCE));
            inventory.setServiceId((Integer)dbMap.get(SERVICE_ID));
            inventory.setName((String)dbMap.get(NAME));
            inventory.setSrcSpanType((Integer)dbMap.get(SRC_SPAN_TYPE));
            inventory.setRegisterTime((Long)dbMap.get(REGISTER_TIME));
            inventory.setHeartbeatTime((Long)dbMap.get(HEARTBEAT_TIME));
            return inventory;
        }

        @Override public Map<String, Object> data2Map(EndpointInventory storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put(SEQUENCE, storageData.getSequence());
            map.put(SERVICE_ID, storageData.getServiceId());
            map.put(NAME, storageData.getName());
            map.put(SRC_SPAN_TYPE, storageData.getSrcSpanType());
            map.put(REGISTER_TIME, storageData.getRegisterTime());
            map.put(HEARTBEAT_TIME, storageData.getHeartbeatTime());
            return map;
        }
    }
}
