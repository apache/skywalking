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

package org.apache.skywalking.oap.server.core.analysis.generated.service;

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
@StorageEntity(name = "service_calls_sum", builder = ServiceCallsSumIndicator.Builder.class)
public class ServiceCallsSumIndicator extends SumIndicator implements AlarmSupported {

    @Setter @Getter @Column(columnName = "id") private int id;

    @Override public String id() {
        String splitJointId = String.valueOf(getTimeBucket());
        splitJointId += Const.ID_SPLIT + String.valueOf(id);
        return splitJointId;
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + id;
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

        ServiceCallsSumIndicator indicator = (ServiceCallsSumIndicator)obj;
        if (id != indicator.id)
            return false;

        if (getTimeBucket() != indicator.getTimeBucket())
            return false;

        return true;
    }

    @Override public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();

        remoteBuilder.setDataLongs(0, getValue());
        remoteBuilder.setDataLongs(1, getTimeBucket());


        remoteBuilder.setDataIntegers(0, getId());

        return remoteBuilder;
    }

    @Override public void deserialize(RemoteData remoteData) {

        setValue(remoteData.getDataLongs(0));
        setTimeBucket(remoteData.getDataLongs(1));


        setId(remoteData.getDataIntegers(0));


    }

    @Override public AlarmMeta getAlarmMeta() {
        return new AlarmMeta("Service_Calls_Sum", Scope.Service, id);
    }

    @Override
    public Indicator toHour() {
        ServiceCallsSumIndicator indicator = new ServiceCallsSumIndicator();
        indicator.setTimeBucket(toTimeBucketInHour());
        indicator.setId(this.getId());
        indicator.setValue(this.getValue());
        indicator.setTimeBucket(this.getTimeBucket());
        return indicator;
    }

    @Override
    public Indicator toDay() {
        ServiceCallsSumIndicator indicator = new ServiceCallsSumIndicator();
        indicator.setTimeBucket(toTimeBucketInDay());
        indicator.setId(this.getId());
        indicator.setValue(this.getValue());
        indicator.setTimeBucket(this.getTimeBucket());
        return indicator;
    }

    @Override
    public Indicator toMonth() {
        ServiceCallsSumIndicator indicator = new ServiceCallsSumIndicator();
        indicator.setTimeBucket(toTimeBucketInMonth());
        indicator.setId(this.getId());
        indicator.setValue(this.getValue());
        indicator.setTimeBucket(this.getTimeBucket());
        return indicator;
    }

    public static class Builder implements StorageBuilder<ServiceCallsSumIndicator> {

        @Override public Map<String, Object> data2Map(ServiceCallsSumIndicator storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", storageData.getId());
            map.put("value", storageData.getValue());
            map.put("time_bucket", storageData.getTimeBucket());
            return map;
        }

        @Override public ServiceCallsSumIndicator map2Data(Map<String, Object> dbMap) {
            ServiceCallsSumIndicator indicator = new ServiceCallsSumIndicator();
            indicator.setId(((Number)dbMap.get("id")).intValue());
            indicator.setValue(((Number)dbMap.get("value")).longValue());
            indicator.setTimeBucket(((Number)dbMap.get("time_bucket")).longValue());
            return indicator;
        }
    }
}
