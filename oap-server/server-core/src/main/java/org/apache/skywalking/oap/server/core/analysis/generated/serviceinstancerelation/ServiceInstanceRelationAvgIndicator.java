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

package org.apache.skywalking.oap.server.core.analysis.generated.serviceinstancerelation;

import java.util.*;
import lombok.*;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.alarm.AlarmMeta;
import org.apache.skywalking.oap.server.core.alarm.AlarmSupported;
import org.apache.skywalking.oap.server.core.analysis.indicator.*;
import org.apache.skywalking.oap.server.core.analysis.indicator.annotation.IndicatorType;
import org.apache.skywalking.oap.server.core.remote.annotation.StreamData;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.annotation.*;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.source.Scope;

/**
 * This class is auto generated. Please don't change this class manually.
 *
 * @author Observability Analysis Language code generator
 */
@IndicatorType
@StreamData
@StorageEntity(name = "serviceinstancerelation_avg", builder = ServiceInstanceRelationAvgIndicator.Builder.class)
public class ServiceInstanceRelationAvgIndicator extends LongAvgIndicator implements AlarmSupported {

    @Setter @Getter @Column(columnName = "source_service_id") private int sourceServiceId;
    @Setter @Getter @Column(columnName = "dest_service_id") private int destServiceId;
    @Setter @Getter @Column(columnName = "source_service_instance_id") private int sourceServiceInstanceId;
    @Setter @Getter @Column(columnName = "dest_service_instance_id") private int destServiceInstanceId;

    @Override public String id() {
        String splitJointId = String.valueOf(getTimeBucket());
        splitJointId += Const.ID_SPLIT + String.valueOf(sourceServiceInstanceId);
        splitJointId += Const.ID_SPLIT + String.valueOf(destServiceInstanceId);
        return splitJointId;
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + sourceServiceInstanceId;
        result = 31 * result + destServiceInstanceId;
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

        ServiceInstanceRelationAvgIndicator indicator = (ServiceInstanceRelationAvgIndicator)obj;
        if (sourceServiceInstanceId != indicator.sourceServiceInstanceId)
            return false;
        if (destServiceInstanceId != indicator.destServiceInstanceId)
            return false;

        if (getTimeBucket() != indicator.getTimeBucket())
            return false;

        return true;
    }

    @Override public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();

        remoteBuilder.setDataLongs(0, getSummation());
        remoteBuilder.setDataLongs(1, getValue());
        remoteBuilder.setDataLongs(2, getTimeBucket());


        remoteBuilder.setDataIntegers(0, getSourceServiceId());
        remoteBuilder.setDataIntegers(1, getDestServiceId());
        remoteBuilder.setDataIntegers(2, getSourceServiceInstanceId());
        remoteBuilder.setDataIntegers(3, getDestServiceInstanceId());
        remoteBuilder.setDataIntegers(4, getCount());

        return remoteBuilder;
    }

    @Override public void deserialize(RemoteData remoteData) {

        setSummation(remoteData.getDataLongs(0));
        setValue(remoteData.getDataLongs(1));
        setTimeBucket(remoteData.getDataLongs(2));


        setSourceServiceId(remoteData.getDataIntegers(0));
        setDestServiceId(remoteData.getDataIntegers(1));
        setSourceServiceInstanceId(remoteData.getDataIntegers(2));
        setDestServiceInstanceId(remoteData.getDataIntegers(3));
        setCount(remoteData.getDataIntegers(4));


    }

    @Override public AlarmMeta getAlarmMeta() {
        return new AlarmMeta("ServiceInstanceRelation_Avg", Scope.ServiceInstanceRelation, sourceServiceId, destServiceId, sourceServiceInstanceId, destServiceInstanceId);
    }

    @Override
    public Indicator toHour() {
        ServiceInstanceRelationAvgIndicator indicator = new ServiceInstanceRelationAvgIndicator();
        indicator.setTimeBucket(toTimeBucketInHour());
        indicator.setSourceServiceId(this.getSourceServiceId());
        indicator.setDestServiceId(this.getDestServiceId());
        indicator.setSourceServiceInstanceId(this.getSourceServiceInstanceId());
        indicator.setDestServiceInstanceId(this.getDestServiceInstanceId());
        indicator.setSummation(this.getSummation());
        indicator.setCount(this.getCount());
        indicator.setValue(this.getValue());
        indicator.setTimeBucket(this.getTimeBucket());
        return indicator;
    }

    @Override
    public Indicator toDay() {
        ServiceInstanceRelationAvgIndicator indicator = new ServiceInstanceRelationAvgIndicator();
        indicator.setTimeBucket(toTimeBucketInDay());
        indicator.setSourceServiceId(this.getSourceServiceId());
        indicator.setDestServiceId(this.getDestServiceId());
        indicator.setSourceServiceInstanceId(this.getSourceServiceInstanceId());
        indicator.setDestServiceInstanceId(this.getDestServiceInstanceId());
        indicator.setSummation(this.getSummation());
        indicator.setCount(this.getCount());
        indicator.setValue(this.getValue());
        indicator.setTimeBucket(this.getTimeBucket());
        return indicator;
    }

    @Override
    public Indicator toMonth() {
        ServiceInstanceRelationAvgIndicator indicator = new ServiceInstanceRelationAvgIndicator();
        indicator.setTimeBucket(toTimeBucketInMonth());
        indicator.setSourceServiceId(this.getSourceServiceId());
        indicator.setDestServiceId(this.getDestServiceId());
        indicator.setSourceServiceInstanceId(this.getSourceServiceInstanceId());
        indicator.setDestServiceInstanceId(this.getDestServiceInstanceId());
        indicator.setSummation(this.getSummation());
        indicator.setCount(this.getCount());
        indicator.setValue(this.getValue());
        indicator.setTimeBucket(this.getTimeBucket());
        return indicator;
    }

    public static class Builder implements StorageBuilder<ServiceInstanceRelationAvgIndicator> {

        @Override public Map<String, Object> data2Map(ServiceInstanceRelationAvgIndicator storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put("source_service_id", storageData.getSourceServiceId());
            map.put("dest_service_id", storageData.getDestServiceId());
            map.put("source_service_instance_id", storageData.getSourceServiceInstanceId());
            map.put("dest_service_instance_id", storageData.getDestServiceInstanceId());
            map.put("summation", storageData.getSummation());
            map.put("count", storageData.getCount());
            map.put("value", storageData.getValue());
            map.put("time_bucket", storageData.getTimeBucket());
            return map;
        }

        @Override public ServiceInstanceRelationAvgIndicator map2Data(Map<String, Object> dbMap) {
            ServiceInstanceRelationAvgIndicator indicator = new ServiceInstanceRelationAvgIndicator();
            indicator.setSourceServiceId(((Number)dbMap.get("source_service_id")).intValue());
            indicator.setDestServiceId(((Number)dbMap.get("dest_service_id")).intValue());
            indicator.setSourceServiceInstanceId(((Number)dbMap.get("source_service_instance_id")).intValue());
            indicator.setDestServiceInstanceId(((Number)dbMap.get("dest_service_instance_id")).intValue());
            indicator.setSummation(((Number)dbMap.get("summation")).longValue());
            indicator.setCount(((Number)dbMap.get("count")).intValue());
            indicator.setValue(((Number)dbMap.get("value")).longValue());
            indicator.setTimeBucket(((Number)dbMap.get("time_bucket")).longValue());
            return indicator;
        }
    }
}
