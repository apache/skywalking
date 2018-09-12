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

package org.apache.skywalking.oap.server.core.analysis.manual.service;

import java.util.*;
import lombok.*;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.analysis.indicator.annotation.IndicatorType;
import org.apache.skywalking.oap.server.core.remote.annotation.StreamData;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.*;

/**
 * @author peng-yongsheng
 */
@IndicatorType
@StreamData
@StorageEntity(name = ServiceComponentIndicator.INDEX_NAME, builder = ServiceComponentIndicator.Builder.class)
public class ServiceComponentIndicator extends Indicator {

    public static final String INDEX_NAME = "service_component";
    public static final String SERVICE_ID = "service_id";
    public static final String COMPONENT_ID = "component_id";

    @Setter @Getter @Column(columnName = SERVICE_ID) private int serviceId;
    @Setter @Getter @Column(columnName = COMPONENT_ID) private int componentId;

    @Override public String id() {
        String splitJointId = String.valueOf(getTimeBucket());
        splitJointId += Const.ID_SPLIT + String.valueOf(serviceId);
        splitJointId += Const.ID_SPLIT + String.valueOf(componentId);
        return splitJointId;
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + serviceId;
        result = 31 * result + componentId;
        result = 31 * result + (int)getTimeBucket();
        return result;
    }

    @Override public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        ServiceComponentIndicator indicator = (ServiceComponentIndicator)obj;
        if (serviceId != indicator.serviceId)
            return false;
        if (componentId != indicator.componentId)
            return false;

        if (getTimeBucket() != indicator.getTimeBucket())
            return false;

        return true;
    }

    @Override public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();

        remoteBuilder.setDataLongs(0, getTimeBucket());

        remoteBuilder.setDataIntegers(0, getServiceId());
        remoteBuilder.setDataIntegers(1, getComponentId());

        return remoteBuilder;
    }

    @Override public void deserialize(RemoteData remoteData) {
        setTimeBucket(remoteData.getDataLongs(0));

        setServiceId(remoteData.getDataIntegers(0));
        setComponentId(remoteData.getDataIntegers(1));
    }

    @Override public void calculate() {
    }

    @Override public final void combine(Indicator indicator) {
    }

    public static class Builder implements StorageBuilder<ServiceComponentIndicator> {

        @Override public Map<String, Object> data2Map(ServiceComponentIndicator storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put(SERVICE_ID, storageData.getServiceId());
            map.put(COMPONENT_ID, storageData.getComponentId());
            map.put(TIME_BUCKET, storageData.getTimeBucket());
            return map;
        }

        @Override public ServiceComponentIndicator map2Data(Map<String, Object> dbMap) {
            ServiceComponentIndicator indicator = new ServiceComponentIndicator();
            indicator.setServiceId(((Number)dbMap.get(SERVICE_ID)).intValue());
            indicator.setComponentId(((Number)dbMap.get(COMPONENT_ID)).intValue());
            indicator.setTimeBucket(((Number)dbMap.get(TIME_BUCKET)).longValue());
            return indicator;
        }
    }
}
