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
@StorageEntity(name = "endpoint_percent", builder = EndpointPercentIndicator.Builder.class)
public class EndpointPercentIndicator extends PercentIndicator implements AlarmSupported {

    @Setter @Getter @Column(columnName = "id") private int id;
    @Setter @Getter @Column(columnName = "service_id") private int serviceId;
    @Setter @Getter @Column(columnName = "service_instance_id") private int serviceInstanceId;

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

        EndpointPercentIndicator indicator = (EndpointPercentIndicator)obj;
        if (id != indicator.id)
            return false;

        if (getTimeBucket() != indicator.getTimeBucket())
            return false;

        return true;
    }

    @Override public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();

        remoteBuilder.setDataLongs(0, getTotal());
        remoteBuilder.setDataLongs(1, getMatch());
        remoteBuilder.setDataLongs(2, getTimeBucket());


        remoteBuilder.setDataIntegers(0, getId());
        remoteBuilder.setDataIntegers(1, getServiceId());
        remoteBuilder.setDataIntegers(2, getServiceInstanceId());
        remoteBuilder.setDataIntegers(3, getPercentage());

        return remoteBuilder;
    }

    @Override public void deserialize(RemoteData remoteData) {

        setTotal(remoteData.getDataLongs(0));
        setMatch(remoteData.getDataLongs(1));
        setTimeBucket(remoteData.getDataLongs(2));


        setId(remoteData.getDataIntegers(0));
        setServiceId(remoteData.getDataIntegers(1));
        setServiceInstanceId(remoteData.getDataIntegers(2));
        setPercentage(remoteData.getDataIntegers(3));


    }

    @Override public AlarmMeta getAlarmMeta() {
        return new AlarmMeta("endpoint_percent", Scope.Endpoint, id, serviceId, serviceInstanceId);
    }

    @Override
    public Indicator toHour() {
        EndpointPercentIndicator indicator = new EndpointPercentIndicator();
        indicator.setTimeBucket(toTimeBucketInHour();
        indicator.setId(this.getId());
        indicator.setServiceId(this.getServiceId());
        indicator.setServiceInstanceId(this.getServiceInstanceId());
        indicator.setTotal(this.getTotal());
        indicator.setPercentage(this.getPercentage());
        indicator.setMatch(this.getMatch());
        indicator.setTimeBucket(this.getTimeBucket());
        return indicator;
    }

    @Override
    public Indicator toDay() {
EndpointPercentIndicator indicator = new EndpointPercentIndicator();
        indicator.setTimeBucket(toTimeBucketInDay();
        indicator.setId(this.getId());
        indicator.setServiceId(this.getServiceId());
        indicator.setServiceInstanceId(this.getServiceInstanceId());
        indicator.setTotal(this.getTotal());
        indicator.setPercentage(this.getPercentage());
        indicator.setMatch(this.getMatch());
        indicator.setTimeBucket(this.getTimeBucket());
        return indicator;
    }

    @Override
    public Indicator toTimeBucketInMonth() {
EndpointPercentIndicator indicator = new EndpointPercentIndicator();
        indicator.setTimeBucket(toTimeBucketInHour();
        indicator.setId(this.getId());
        indicator.setServiceId(this.getServiceId());
        indicator.setServiceInstanceId(this.getServiceInstanceId());
        indicator.setTotal(this.getTotal());
        indicator.setPercentage(this.getPercentage());
        indicator.setMatch(this.getMatch());
        indicator.setTimeBucket(this.getTimeBucket());
        return indicator;
    }

    public static class Builder implements StorageBuilder<EndpointPercentIndicator> {

        @Override public Map<String, Object> data2Map(EndpointPercentIndicator storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", storageData.getId());
            map.put("service_id", storageData.getServiceId());
            map.put("service_instance_id", storageData.getServiceInstanceId());
            map.put("total", storageData.getTotal());
            map.put("percentage", storageData.getPercentage());
            map.put("match", storageData.getMatch());
            map.put("time_bucket", storageData.getTimeBucket());
            return map;
        }

        @Override public EndpointPercentIndicator map2Data(Map<String, Object> dbMap) {
            EndpointPercentIndicator indicator = new EndpointPercentIndicator();
            indicator.setId(((Number)dbMap.get("id")).intValue());
            indicator.setServiceId(((Number)dbMap.get("service_id")).intValue());
            indicator.setServiceInstanceId(((Number)dbMap.get("service_instance_id")).intValue());
            indicator.setTotal(((Number)dbMap.get("total")).longValue());
            indicator.setPercentage(((Number)dbMap.get("percentage")).intValue());
            indicator.setMatch(((Number)dbMap.get("match")).longValue());
            indicator.setTimeBucket(((Number)dbMap.get("time_bucket")).longValue());
            return indicator;
        }
    }
}
