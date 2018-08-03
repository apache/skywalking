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

package org.apache.skywalking.oap.server.core.analysis.endpoint;

import java.util.*;
import lombok.*;
import org.apache.skywalking.oap.server.core.analysis.indicator.*;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

/**
 * @author peng-yongsheng
 */
public class EndpointLatencyAvgIndicator extends AvgIndicator {

    private static final String NAME = "endpoint_latency_avg";
    private static final String ID = "id";
    private static final String SERVICE_ID = "service_id";
    private static final String SERVICE_INSTANCE_ID = "service_instance_id";

    @Setter @Getter @Column(columnName = ID) private int id;
    @Setter @Getter @Column(columnName = SERVICE_ID) private int serviceId;
    @Setter @Getter @Column(columnName = SERVICE_INSTANCE_ID) private int serviceInstanceId;

    @Override public String name() {
        return NAME;
    }

    @Override public String id() {
        return String.valueOf(id);
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

        EndpointLatencyAvgIndicator indicator = (EndpointLatencyAvgIndicator)obj;
        if (id != indicator.id)
            return false;
        if (getTimeBucket() != indicator.getTimeBucket())
            return false;

        return true;
    }

    @Override public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.setDataIntegers(0, getId());
        remoteBuilder.setDataIntegers(1, getServiceId());
        remoteBuilder.setDataIntegers(2, getServiceInstanceId());
        remoteBuilder.setDataIntegers(3, getCount());

        remoteBuilder.setDataLongs(0, getTimeBucket());
        remoteBuilder.setDataLongs(1, getSummation());
        remoteBuilder.setDataLongs(2, getValue());

        return remoteBuilder;
    }

    @Override public void deserialize(RemoteData remoteData) {
        setId(remoteData.getDataIntegers(0));
        setServiceId(remoteData.getDataIntegers(1));
        setServiceInstanceId(remoteData.getDataIntegers(2));
        setCount(remoteData.getDataIntegers(3));

        setTimeBucket(remoteData.getDataLongs(0));
        setSummation(remoteData.getDataLongs(1));
        setValue(remoteData.getDataLongs(2));
    }

    @Override public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(ID, id);
        map.put(SERVICE_ID, serviceId);
        map.put(SERVICE_INSTANCE_ID, serviceInstanceId);
        map.put(COUNT, getCount());
        map.put(SUMMATION, getSummation());
        map.put(VALUE, getValue());
        map.put(TIME_BUCKET, getTimeBucket());
        return map;
    }

    @Override public Indicator newOne(Map<String, Object> dbMap) {
        EndpointLatencyAvgIndicator indicator = new EndpointLatencyAvgIndicator();
        indicator.setId((Integer)dbMap.get(ID));
        indicator.setServiceId((Integer)dbMap.get(SERVICE_ID));
        indicator.setServiceInstanceId((Integer)dbMap.get(SERVICE_INSTANCE_ID));
        indicator.setCount((Integer)dbMap.get(COUNT));
        indicator.setSummation((Long)dbMap.get(SUMMATION));
        indicator.setValue((Long)dbMap.get(VALUE));
        indicator.setTimeBucket((Long)dbMap.get(TIME_BUCKET));
        return indicator;
    }
}
