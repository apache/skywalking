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

package org.apache.skywalking.oap.server.core.analysis.generated.endpoint;

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
@StorageEntity(name = "endpoint_cpm", builder = EndpointCpmIndicator.Builder.class, source = Scope.Endpoint)
public class EndpointCpmIndicator extends CPMIndicator implements AlarmSupported {

    @Setter @Getter @Column(columnName = "entity_id") @IDColumn private java.lang.String entityId;
    @Setter @Getter @Column(columnName = "service_id")  private int serviceId;
    @Setter @Getter @Column(columnName = "service_instance_id")  private int serviceInstanceId;

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

        EndpointCpmIndicator indicator = (EndpointCpmIndicator)obj;
        if (!entityId.equals(indicator.entityId))
            return false;

        if (getTimeBucket() != indicator.getTimeBucket())
            return false;

        return true;
    }

    @Override public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.addDataStrings(getEntityId());

        remoteBuilder.addDataLongs(getValue());
        remoteBuilder.addDataLongs(getTotal());
        remoteBuilder.addDataLongs(getTimeBucket());


        remoteBuilder.addDataIntegers(getServiceId());
        remoteBuilder.addDataIntegers(getServiceInstanceId());

        return remoteBuilder;
    }

    @Override public void deserialize(RemoteData remoteData) {
        setEntityId(remoteData.getDataStrings(0));

        setValue(remoteData.getDataLongs(0));
        setTotal(remoteData.getDataLongs(1));
        setTimeBucket(remoteData.getDataLongs(2));


        setServiceId(remoteData.getDataIntegers(0));
        setServiceInstanceId(remoteData.getDataIntegers(1));


    }

    @Override public AlarmMeta getAlarmMeta() {
        return new AlarmMeta("endpoint_cpm", Scope.Endpoint, entityId);
    }

    @Override
    public Indicator toHour() {
        EndpointCpmIndicator indicator = new EndpointCpmIndicator();
        indicator.setEntityId(this.getEntityId());
        indicator.setServiceId(this.getServiceId());
        indicator.setServiceInstanceId(this.getServiceInstanceId());
        indicator.setValue(this.getValue());
        indicator.setTotal(this.getTotal());
        indicator.setTimeBucket(toTimeBucketInHour());
        return indicator;
    }

    @Override
    public Indicator toDay() {
        EndpointCpmIndicator indicator = new EndpointCpmIndicator();
        indicator.setEntityId(this.getEntityId());
        indicator.setServiceId(this.getServiceId());
        indicator.setServiceInstanceId(this.getServiceInstanceId());
        indicator.setValue(this.getValue());
        indicator.setTotal(this.getTotal());
        indicator.setTimeBucket(toTimeBucketInDay());
        return indicator;
    }

    @Override
    public Indicator toMonth() {
        EndpointCpmIndicator indicator = new EndpointCpmIndicator();
        indicator.setEntityId(this.getEntityId());
        indicator.setServiceId(this.getServiceId());
        indicator.setServiceInstanceId(this.getServiceInstanceId());
        indicator.setValue(this.getValue());
        indicator.setTotal(this.getTotal());
        indicator.setTimeBucket(toTimeBucketInMonth());
        return indicator;
    }

    public static class Builder implements StorageBuilder<EndpointCpmIndicator> {

        @Override public Map<String, Object> data2Map(EndpointCpmIndicator storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put("entity_id", storageData.getEntityId());
            map.put("service_id", storageData.getServiceId());
            map.put("service_instance_id", storageData.getServiceInstanceId());
            map.put("value", storageData.getValue());
            map.put("total", storageData.getTotal());
            map.put("time_bucket", storageData.getTimeBucket());
            return map;
        }

        @Override public EndpointCpmIndicator map2Data(Map<String, Object> dbMap) {
            EndpointCpmIndicator indicator = new EndpointCpmIndicator();
            indicator.setEntityId((String)dbMap.get("entity_id"));
            indicator.setServiceId(((Number)dbMap.get("service_id")).intValue());
            indicator.setServiceInstanceId(((Number)dbMap.get("service_instance_id")).intValue());
            indicator.setValue(((Number)dbMap.get("value")).longValue());
            indicator.setTotal(((Number)dbMap.get("total")).longValue());
            indicator.setTimeBucket(((Number)dbMap.get("time_bucket")).longValue());
            return indicator;
        }
    }
}
