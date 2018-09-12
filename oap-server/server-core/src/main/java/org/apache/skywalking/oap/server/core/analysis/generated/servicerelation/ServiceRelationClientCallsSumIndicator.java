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

package org.apache.skywalking.oap.server.core.analysis.generated.servicerelation;

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
@StorageEntity(name = "service_relation_client_calls_sum", builder = ServiceRelationClientCallsSumIndicator.Builder.class)
public class ServiceRelationClientCallsSumIndicator extends SumIndicator implements AlarmSupported {

    @Setter @Getter @Column(columnName = "source_service_id") private int sourceServiceId;
    @Setter @Getter @Column(columnName = "dest_service_id") private int destServiceId;

    @Override public String id() {
        String splitJointId = String.valueOf(getTimeBucket());
        splitJointId += Const.ID_SPLIT + String.valueOf(sourceServiceId);
        splitJointId += Const.ID_SPLIT + String.valueOf(destServiceId);
        return splitJointId;
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + sourceServiceId;
        result = 31 * result + destServiceId;
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

        ServiceRelationClientCallsSumIndicator indicator = (ServiceRelationClientCallsSumIndicator)obj;
        if (sourceServiceId != indicator.sourceServiceId)
            return false;
        if (destServiceId != indicator.destServiceId)
            return false;

        if (getTimeBucket() != indicator.getTimeBucket())
            return false;

        return true;
    }

    @Override public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();

        remoteBuilder.setDataLongs(0, getValue());
        remoteBuilder.setDataLongs(1, getTimeBucket());


        remoteBuilder.setDataIntegers(0, getSourceServiceId());
        remoteBuilder.setDataIntegers(1, getDestServiceId());

        return remoteBuilder;
    }

    @Override public void deserialize(RemoteData remoteData) {

        setValue(remoteData.getDataLongs(0));
        setTimeBucket(remoteData.getDataLongs(1));


        setSourceServiceId(remoteData.getDataIntegers(0));
        setDestServiceId(remoteData.getDataIntegers(1));


    }

    @Override public AlarmMeta getAlarmMeta() {
        return new AlarmMeta("Service_Relation_Client_Calls_Sum", Scope.ServiceRelation, sourceServiceId, destServiceId);
    }

    @Override
    public Indicator toHour() {
        ServiceRelationClientCallsSumIndicator indicator = new ServiceRelationClientCallsSumIndicator();
        indicator.setTimeBucket(toTimeBucketInHour());
        indicator.setSourceServiceId(this.getSourceServiceId());
        indicator.setDestServiceId(this.getDestServiceId());
        indicator.setValue(this.getValue());
        indicator.setTimeBucket(this.getTimeBucket());
        return indicator;
    }

    @Override
    public Indicator toDay() {
        ServiceRelationClientCallsSumIndicator indicator = new ServiceRelationClientCallsSumIndicator();
        indicator.setTimeBucket(toTimeBucketInDay());
        indicator.setSourceServiceId(this.getSourceServiceId());
        indicator.setDestServiceId(this.getDestServiceId());
        indicator.setValue(this.getValue());
        indicator.setTimeBucket(this.getTimeBucket());
        return indicator;
    }

    @Override
    public Indicator toMonth() {
        ServiceRelationClientCallsSumIndicator indicator = new ServiceRelationClientCallsSumIndicator();
        indicator.setTimeBucket(toTimeBucketInMonth());
        indicator.setSourceServiceId(this.getSourceServiceId());
        indicator.setDestServiceId(this.getDestServiceId());
        indicator.setValue(this.getValue());
        indicator.setTimeBucket(this.getTimeBucket());
        return indicator;
    }

    public static class Builder implements StorageBuilder<ServiceRelationClientCallsSumIndicator> {

        @Override public Map<String, Object> data2Map(ServiceRelationClientCallsSumIndicator storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put("source_service_id", storageData.getSourceServiceId());
            map.put("dest_service_id", storageData.getDestServiceId());
            map.put("value", storageData.getValue());
            map.put("time_bucket", storageData.getTimeBucket());
            return map;
        }

        @Override public ServiceRelationClientCallsSumIndicator map2Data(Map<String, Object> dbMap) {
            ServiceRelationClientCallsSumIndicator indicator = new ServiceRelationClientCallsSumIndicator();
            indicator.setSourceServiceId(((Number)dbMap.get("source_service_id")).intValue());
            indicator.setDestServiceId(((Number)dbMap.get("dest_service_id")).intValue());
            indicator.setValue(((Number)dbMap.get("value")).longValue());
            indicator.setTimeBucket(((Number)dbMap.get("time_bucket")).longValue());
            return indicator;
        }
    }
}
