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

package org.apache.skywalking.oap.server.core.analysis.generated.service.serviceavg;

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
@StorageEntity(name = "service_avg", builder = ServiceAvgIndicator.Builder.class, source = Scope.Service)
public class ServiceAvgIndicator extends LongAvgIndicator implements AlarmSupported {

    @Setter @Getter @Column(columnName = "entity_id") @IDColumn private java.lang.String entityId;

    @Override public String id() {
        String splitJointId = String.valueOf(getTimeBucket());
        splitJointId += Const.ID_SPLIT + entityId;
        return splitJointId;
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + entityId.hashCode();
        result = 31 * result + (int)getTimeBucket();
        return result;
    }

    @Override public int remoteHashCode() {
        int result = 17;
        result = 31 * result + entityId.hashCode();
        return result;
    }

    @Override public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        ServiceAvgIndicator indicator = (ServiceAvgIndicator)obj;
        if (!entityId.equals(indicator.entityId))
            return false;

        if (getTimeBucket() != indicator.getTimeBucket())
            return false;

        return true;
    }

    @Override public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.addDataStrings(getEntityId());
        remoteBuilder.addDataStrings(getStringField());

        remoteBuilder.addDataLongs(getSummation());
        remoteBuilder.addDataLongs(getValue());
        remoteBuilder.addDataLongs(getTimeBucket());


        remoteBuilder.addDataIntegers(getCount());

        return remoteBuilder;
    }

    @Override public void deserialize(RemoteData remoteData) {
        setEntityId(remoteData.getDataStrings(0));
        setStringField(remoteData.getDataStrings(1));

        setSummation(remoteData.getDataLongs(0));
        setValue(remoteData.getDataLongs(1));
        setTimeBucket(remoteData.getDataLongs(2));


        setCount(remoteData.getDataIntegers(0));


    }

    @Override public AlarmMeta getAlarmMeta() {
        return new AlarmMeta("generate_indicator", Scope.Service, entityId);
    }

    @Override
    public Indicator toHour() {
        ServiceAvgIndicator indicator = new ServiceAvgIndicator();
        indicator.setEntityId(this.getEntityId());
        indicator.setSummation(this.getSummation());
        indicator.setCount(this.getCount());
        indicator.setValue(this.getValue());
        indicator.setTimeBucket(toTimeBucketInHour());
        indicator.setStringField(this.getStringField());
        return indicator;
    }

    @Override
    public Indicator toDay() {
        ServiceAvgIndicator indicator = new ServiceAvgIndicator();
        indicator.setEntityId(this.getEntityId());
        indicator.setSummation(this.getSummation());
        indicator.setCount(this.getCount());
        indicator.setValue(this.getValue());
        indicator.setTimeBucket(toTimeBucketInDay());
        indicator.setStringField(this.getStringField());
        return indicator;
    }

    @Override
    public Indicator toMonth() {
        ServiceAvgIndicator indicator = new ServiceAvgIndicator();
        indicator.setEntityId(this.getEntityId());
        indicator.setSummation(this.getSummation());
        indicator.setCount(this.getCount());
        indicator.setValue(this.getValue());
        indicator.setTimeBucket(toTimeBucketInMonth());
        indicator.setStringField(this.getStringField());
        return indicator;
    }

    public static class Builder implements StorageBuilder<ServiceAvgIndicator> {

        @Override public Map<String, Object> data2Map(ServiceAvgIndicator storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put("entity_id", storageData.getEntityId());
            map.put("summation", storageData.getSummation());
            map.put("count", storageData.getCount());
            map.put("value", storageData.getValue());
            map.put("time_bucket", storageData.getTimeBucket());
            map.put("string_field", storageData.getStringField());
            return map;
        }

        @Override public ServiceAvgIndicator map2Data(Map<String, Object> dbMap) {
            ServiceAvgIndicator indicator = new ServiceAvgIndicator();
            indicator.setEntityId((String)dbMap.get("entity_id"));
            indicator.setSummation(((Number)dbMap.get("summation")).longValue());
            indicator.setCount(((Number)dbMap.get("count")).intValue());
            indicator.setValue(((Number)dbMap.get("value")).longValue());
            indicator.setTimeBucket(((Number)dbMap.get("time_bucket")).longValue());
            indicator.setStringField((String)dbMap.get("string_field"));
            return indicator;
        }
    }
}
