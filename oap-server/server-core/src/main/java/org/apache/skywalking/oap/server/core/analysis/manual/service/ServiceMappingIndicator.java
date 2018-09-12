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

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.analysis.indicator.annotation.IndicatorType;
import org.apache.skywalking.oap.server.core.remote.annotation.StreamData;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.StorageEntity;

/**
 * @author peng-yongsheng
 */
@IndicatorType
@StreamData
@StorageEntity(name = ServiceMappingIndicator.INDEX_NAME, builder = ServiceMappingIndicator.Builder.class)
public class ServiceMappingIndicator extends Indicator {

    public static final String INDEX_NAME = "service_mapping";
    public static final String SERVICE_ID = "service_id";
    public static final String MAPPING_SERVICE_ID = "mapping_service_id";

    @Setter @Getter @Column(columnName = SERVICE_ID) private int serviceId;
    @Setter @Getter @Column(columnName = MAPPING_SERVICE_ID) private int mappingServiceId;

    @Override public String id() {
        String splitJointId = String.valueOf(getTimeBucket());
        splitJointId += Const.ID_SPLIT + String.valueOf(serviceId);
        splitJointId += Const.ID_SPLIT + String.valueOf(mappingServiceId);
        return splitJointId;
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + serviceId;
        result = 31 * result + mappingServiceId;
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

        ServiceMappingIndicator indicator = (ServiceMappingIndicator)obj;
        if (serviceId != indicator.serviceId)
            return false;
        if (mappingServiceId != indicator.mappingServiceId)
            return false;

        if (getTimeBucket() != indicator.getTimeBucket())
            return false;

        return true;
    }

    @Override public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();

        remoteBuilder.setDataLongs(0, getTimeBucket());

        remoteBuilder.setDataIntegers(0, getServiceId());
        remoteBuilder.setDataIntegers(1, getMappingServiceId());

        return remoteBuilder;
    }

    @Override public void deserialize(RemoteData remoteData) {
        setTimeBucket(remoteData.getDataLongs(0));

        setServiceId(remoteData.getDataIntegers(0));
        setMappingServiceId(remoteData.getDataIntegers(1));
    }

    @Override public void calculate() {
    }

    @Override public Indicator toHour() {
        ServiceMappingIndicator indicator = new ServiceMappingIndicator();
        indicator.setTimeBucket(toTimeBucketInHour());
        indicator.setServiceId(this.getServiceId());
        indicator.setMappingServiceId(this.getMappingServiceId());

        return indicator;
    }

    @Override public Indicator toDay() {
        ServiceMappingIndicator indicator = new ServiceMappingIndicator();
        indicator.setTimeBucket(toTimeBucketInDay());
        indicator.setServiceId(this.getServiceId());
        indicator.setMappingServiceId(this.getMappingServiceId());

        return indicator;
    }

    @Override public Indicator toMonth() {
        ServiceMappingIndicator indicator = new ServiceMappingIndicator();
        indicator.setTimeBucket(toTimeBucketInMonth());
        indicator.setServiceId(this.getServiceId());
        indicator.setMappingServiceId(this.getMappingServiceId());

        return indicator;
    }

    @Override public final void combine(Indicator indicator) {
    }

    public static class Builder implements StorageBuilder<ServiceMappingIndicator> {

        @Override public Map<String, Object> data2Map(ServiceMappingIndicator storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put(SERVICE_ID, storageData.getServiceId());
            map.put(MAPPING_SERVICE_ID, storageData.getMappingServiceId());
            map.put(TIME_BUCKET, storageData.getTimeBucket());
            return map;
        }

        @Override public ServiceMappingIndicator map2Data(Map<String, Object> dbMap) {
            ServiceMappingIndicator indicator = new ServiceMappingIndicator();
            indicator.setServiceId(((Number)dbMap.get(SERVICE_ID)).intValue());
            indicator.setMappingServiceId(((Number)dbMap.get(MAPPING_SERVICE_ID)).intValue());
            indicator.setTimeBucket(((Number)dbMap.get(TIME_BUCKET)).longValue());
            return indicator;
        }
    }
}
