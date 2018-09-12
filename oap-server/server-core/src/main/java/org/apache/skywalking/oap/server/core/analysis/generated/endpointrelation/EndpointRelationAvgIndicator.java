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

package org.apache.skywalking.oap.server.core.analysis.generated.endpointrelation;

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
@StorageEntity(name = "endpointrelation_avg", builder = EndpointRelationAvgIndicator.Builder.class)
public class EndpointRelationAvgIndicator extends LongAvgIndicator implements AlarmSupported {

    @Setter @Getter @Column(columnName = "endpoint_id") @IDColumn private int endpointId;
    @Setter @Getter @Column(columnName = "child_endpoint_id") @IDColumn private int childEndpointId;
    @Setter @Getter @Column(columnName = "service_id")  private int serviceId;
    @Setter @Getter @Column(columnName = "child_service_id")  private int childServiceId;
    @Setter @Getter @Column(columnName = "service_instance_id")  private int serviceInstanceId;
    @Setter @Getter @Column(columnName = "child_service_instance_id")  private int childServiceInstanceId;

    @Override public String id() {
        String splitJointId = String.valueOf(getTimeBucket());
        splitJointId += Const.ID_SPLIT + String.valueOf(endpointId);
        splitJointId += Const.ID_SPLIT + String.valueOf(childEndpointId);
        return splitJointId;
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + endpointId;
        result = 31 * result + childEndpointId;
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

        EndpointRelationAvgIndicator indicator = (EndpointRelationAvgIndicator)obj;
        if (endpointId != indicator.endpointId)
            return false;
        if (childEndpointId != indicator.childEndpointId)
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


        remoteBuilder.setDataIntegers(0, getEndpointId());
        remoteBuilder.setDataIntegers(1, getChildEndpointId());
        remoteBuilder.setDataIntegers(2, getServiceId());
        remoteBuilder.setDataIntegers(3, getChildServiceId());
        remoteBuilder.setDataIntegers(4, getServiceInstanceId());
        remoteBuilder.setDataIntegers(5, getChildServiceInstanceId());
        remoteBuilder.setDataIntegers(6, getCount());

        return remoteBuilder;
    }

    @Override public void deserialize(RemoteData remoteData) {

        setSummation(remoteData.getDataLongs(0));
        setValue(remoteData.getDataLongs(1));
        setTimeBucket(remoteData.getDataLongs(2));


        setEndpointId(remoteData.getDataIntegers(0));
        setChildEndpointId(remoteData.getDataIntegers(1));
        setServiceId(remoteData.getDataIntegers(2));
        setChildServiceId(remoteData.getDataIntegers(3));
        setServiceInstanceId(remoteData.getDataIntegers(4));
        setChildServiceInstanceId(remoteData.getDataIntegers(5));
        setCount(remoteData.getDataIntegers(6));


    }

    @Override public AlarmMeta getAlarmMeta() {
        return new AlarmMeta("EndpointRelation_Avg", Scope.EndpointRelation, endpointId, childEndpointId, serviceId, childServiceId, serviceInstanceId, childServiceInstanceId);
    }

    @Override
    public Indicator toHour() {
        EndpointRelationAvgIndicator indicator = new EndpointRelationAvgIndicator();
        indicator.setTimeBucket(toTimeBucketInHour());
        indicator.setEndpointId(this.getEndpointId());
        indicator.setChildEndpointId(this.getChildEndpointId());
        indicator.setServiceId(this.getServiceId());
        indicator.setChildServiceId(this.getChildServiceId());
        indicator.setServiceInstanceId(this.getServiceInstanceId());
        indicator.setChildServiceInstanceId(this.getChildServiceInstanceId());
        indicator.setSummation(this.getSummation());
        indicator.setCount(this.getCount());
        indicator.setValue(this.getValue());
        indicator.setTimeBucket(this.getTimeBucket());
        return indicator;
    }

    @Override
    public Indicator toDay() {
        EndpointRelationAvgIndicator indicator = new EndpointRelationAvgIndicator();
        indicator.setTimeBucket(toTimeBucketInDay());
        indicator.setEndpointId(this.getEndpointId());
        indicator.setChildEndpointId(this.getChildEndpointId());
        indicator.setServiceId(this.getServiceId());
        indicator.setChildServiceId(this.getChildServiceId());
        indicator.setServiceInstanceId(this.getServiceInstanceId());
        indicator.setChildServiceInstanceId(this.getChildServiceInstanceId());
        indicator.setSummation(this.getSummation());
        indicator.setCount(this.getCount());
        indicator.setValue(this.getValue());
        indicator.setTimeBucket(this.getTimeBucket());
        return indicator;
    }

    @Override
    public Indicator toMonth() {
        EndpointRelationAvgIndicator indicator = new EndpointRelationAvgIndicator();
        indicator.setTimeBucket(toTimeBucketInMonth());
        indicator.setEndpointId(this.getEndpointId());
        indicator.setChildEndpointId(this.getChildEndpointId());
        indicator.setServiceId(this.getServiceId());
        indicator.setChildServiceId(this.getChildServiceId());
        indicator.setServiceInstanceId(this.getServiceInstanceId());
        indicator.setChildServiceInstanceId(this.getChildServiceInstanceId());
        indicator.setSummation(this.getSummation());
        indicator.setCount(this.getCount());
        indicator.setValue(this.getValue());
        indicator.setTimeBucket(this.getTimeBucket());
        return indicator;
    }

    public static class Builder implements StorageBuilder<EndpointRelationAvgIndicator> {

        @Override public Map<String, Object> data2Map(EndpointRelationAvgIndicator storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put("endpoint_id", storageData.getEndpointId());
            map.put("child_endpoint_id", storageData.getChildEndpointId());
            map.put("service_id", storageData.getServiceId());
            map.put("child_service_id", storageData.getChildServiceId());
            map.put("service_instance_id", storageData.getServiceInstanceId());
            map.put("child_service_instance_id", storageData.getChildServiceInstanceId());
            map.put("summation", storageData.getSummation());
            map.put("count", storageData.getCount());
            map.put("value", storageData.getValue());
            map.put("time_bucket", storageData.getTimeBucket());
            return map;
        }

        @Override public EndpointRelationAvgIndicator map2Data(Map<String, Object> dbMap) {
            EndpointRelationAvgIndicator indicator = new EndpointRelationAvgIndicator();
            indicator.setEndpointId(((Number)dbMap.get("endpoint_id")).intValue());
            indicator.setChildEndpointId(((Number)dbMap.get("child_endpoint_id")).intValue());
            indicator.setServiceId(((Number)dbMap.get("service_id")).intValue());
            indicator.setChildServiceId(((Number)dbMap.get("child_service_id")).intValue());
            indicator.setServiceInstanceId(((Number)dbMap.get("service_instance_id")).intValue());
            indicator.setChildServiceInstanceId(((Number)dbMap.get("child_service_instance_id")).intValue());
            indicator.setSummation(((Number)dbMap.get("summation")).longValue());
            indicator.setCount(((Number)dbMap.get("count")).intValue());
            indicator.setValue(((Number)dbMap.get("value")).longValue());
            indicator.setTimeBucket(((Number)dbMap.get("time_bucket")).longValue());
            return indicator;
        }
    }
}
