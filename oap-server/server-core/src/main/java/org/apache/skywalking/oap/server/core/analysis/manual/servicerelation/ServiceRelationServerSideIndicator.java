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

package org.apache.skywalking.oap.server.core.analysis.manual.servicerelation;

import java.util.*;
import lombok.*;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.analysis.indicator.annotation.IndicatorType;
import org.apache.skywalking.oap.server.core.remote.annotation.StreamData;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.source.Scope;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.*;

@IndicatorType
@StreamData
@StorageEntity(name = ServiceRelationServerSideIndicator.INDEX_NAME, builder = ServiceRelationServerSideIndicator.Builder.class,
    source = Scope.ServiceRelation)
public class ServiceRelationServerSideIndicator extends Indicator {

    public static final String INDEX_NAME = "service_relation_server_side";
    public static final String SOURCE_SERVICE_ID = "source_service_id";
    public static final String DEST_SERVICE_ID = "dest_service_id";
    public static final String COMPONENT_ID = "component_id";

    @Setter @Getter @Column(columnName = SOURCE_SERVICE_ID) @IDColumn private int sourceServiceId;
    @Setter @Getter @Column(columnName = DEST_SERVICE_ID) @IDColumn private int destServiceId;
    @Setter @Getter @Column(columnName = COMPONENT_ID) @IDColumn private int componentId;
    @Setter @Getter @Column(columnName = ENTITY_ID) @IDColumn private String entityId;

    @Override public String id() {
        String splitJointId = String.valueOf(getTimeBucket());
        splitJointId += Const.ID_SPLIT + String.valueOf(sourceServiceId);
        splitJointId += Const.ID_SPLIT + String.valueOf(destServiceId);
        splitJointId += Const.ID_SPLIT + String.valueOf(componentId);
        return splitJointId;
    }

    @Override public void combine(Indicator indicator) {

    }

    @Override public void calculate() {

    }

    @Override public Indicator toHour() {
        ServiceRelationServerSideIndicator indicator = new ServiceRelationServerSideIndicator();
        indicator.setTimeBucket(toTimeBucketInHour());
        indicator.setSourceServiceId(getSourceServiceId());
        indicator.setDestServiceId(getDestServiceId());
        indicator.setComponentId(getComponentId());
        indicator.setEntityId(getEntityId());
        return indicator;
    }

    @Override public Indicator toDay() {
        ServiceRelationServerSideIndicator indicator = new ServiceRelationServerSideIndicator();
        indicator.setTimeBucket(toTimeBucketInDay());
        indicator.setSourceServiceId(getSourceServiceId());
        indicator.setDestServiceId(getDestServiceId());
        indicator.setComponentId(getComponentId());
        indicator.setEntityId(getEntityId());
        return indicator;
    }

    @Override public Indicator toMonth() {
        ServiceRelationServerSideIndicator indicator = new ServiceRelationServerSideIndicator();
        indicator.setTimeBucket(toTimeBucketInMonth());
        indicator.setSourceServiceId(getSourceServiceId());
        indicator.setDestServiceId(getDestServiceId());
        indicator.setComponentId(getComponentId());
        indicator.setEntityId(getEntityId());
        return indicator;
    }

    @Override public int remoteHashCode() {
        int result = 17;
        result = 31 * result + sourceServiceId;
        result = 31 * result + destServiceId;
        result = 31 * result + componentId;
        return result;
    }

    @Override public void deserialize(RemoteData remoteData) {
        setEntityId(remoteData.getDataStrings(0));

        setSourceServiceId(remoteData.getDataIntegers(0));
        setDestServiceId(remoteData.getDataIntegers(1));
        setComponentId(remoteData.getDataIntegers(2));

        setTimeBucket(remoteData.getDataLongs(0));
    }

    @Override public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();

        remoteBuilder.addDataIntegers(getSourceServiceId());
        remoteBuilder.addDataIntegers(getDestServiceId());
        remoteBuilder.addDataIntegers(getComponentId());

        remoteBuilder.addDataStrings(getEntityId());

        remoteBuilder.addDataLongs(getTimeBucket());
        return remoteBuilder;
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + sourceServiceId;
        result = 31 * result + destServiceId;
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

        ServiceRelationServerSideIndicator indicator = (ServiceRelationServerSideIndicator)obj;
        if (sourceServiceId != indicator.sourceServiceId)
            return false;
        if (destServiceId != indicator.destServiceId)
            return false;
        if (componentId != indicator.componentId)
            return false;

        if (getTimeBucket() != indicator.getTimeBucket())
            return false;

        return true;
    }

    public static class Builder implements StorageBuilder<ServiceRelationServerSideIndicator> {

        @Override public ServiceRelationServerSideIndicator map2Data(Map<String, Object> dbMap) {
            ServiceRelationServerSideIndicator indicator = new ServiceRelationServerSideIndicator();
            indicator.setEntityId((String)dbMap.get(ENTITY_ID));
            indicator.setSourceServiceId(((Number)dbMap.get(SOURCE_SERVICE_ID)).intValue());
            indicator.setDestServiceId(((Number)dbMap.get(DEST_SERVICE_ID)).intValue());
            indicator.setComponentId(((Number)dbMap.get(COMPONENT_ID)).intValue());
            indicator.setTimeBucket(((Number)dbMap.get(TIME_BUCKET)).longValue());
            return indicator;
        }

        @Override public Map<String, Object> data2Map(ServiceRelationServerSideIndicator storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put(ENTITY_ID, storageData.getEntityId());
            map.put(SOURCE_SERVICE_ID, storageData.getSourceServiceId());
            map.put(DEST_SERVICE_ID, storageData.getDestServiceId());
            map.put(COMPONENT_ID, storageData.getComponentId());
            map.put(TIME_BUCKET, storageData.getTimeBucket());
            return map;
        }
    }
}
