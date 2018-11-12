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

package org.apache.skywalking.oap.server.core.analysis.manual.endpointrelation;

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
@StorageEntity(name = EndpointRelationServerSideIndicator.INDEX_NAME, builder = EndpointRelationServerSideIndicator.Builder.class, source = Scope.EndpointRelation)
public class EndpointRelationServerSideIndicator extends Indicator {

    public static final String INDEX_NAME = "endpoint_relation_server_side";
    public static final String SOURCE_ENDPOINT_ID = "source_endpoint_id";
    public static final String DEST_ENDPOINT_ID = "dest_endpoint_id";
    public static final String COMPONENT_ID = "component_id";

    @Setter @Getter @Column(columnName = SOURCE_ENDPOINT_ID) @IDColumn private int sourceEndpointId;
    @Setter @Getter @Column(columnName = DEST_ENDPOINT_ID) @IDColumn private int destEndpointId;
    @Setter @Getter @Column(columnName = COMPONENT_ID) @IDColumn private int componentId;
    @Setter @Getter @Column(columnName = ENTITY_ID) @IDColumn private String entityId;

    @Override public String id() {
        String splitJointId = String.valueOf(getTimeBucket());
        splitJointId += Const.ID_SPLIT + String.valueOf(sourceEndpointId);
        splitJointId += Const.ID_SPLIT + String.valueOf(destEndpointId);
        splitJointId += Const.ID_SPLIT + String.valueOf(componentId);
        return splitJointId;
    }

    @Override public void combine(Indicator indicator) {

    }

    @Override public void calculate() {

    }

    @Override public Indicator toHour() {
        EndpointRelationServerSideIndicator indicator = new EndpointRelationServerSideIndicator();
        indicator.setTimeBucket(toTimeBucketInHour());
        indicator.setSourceEndpointId(getSourceEndpointId());
        indicator.setDestEndpointId(getDestEndpointId());
        indicator.setComponentId(getComponentId());
        indicator.setEntityId(getEntityId());
        return indicator;
    }

    @Override public Indicator toDay() {
        EndpointRelationServerSideIndicator indicator = new EndpointRelationServerSideIndicator();
        indicator.setTimeBucket(toTimeBucketInDay());
        indicator.setSourceEndpointId(getSourceEndpointId());
        indicator.setDestEndpointId(getDestEndpointId());
        indicator.setComponentId(getComponentId());
        indicator.setEntityId(getEntityId());
        return indicator;
    }

    @Override public Indicator toMonth() {
        EndpointRelationServerSideIndicator indicator = new EndpointRelationServerSideIndicator();
        indicator.setTimeBucket(toTimeBucketInMonth());
        indicator.setSourceEndpointId(getSourceEndpointId());
        indicator.setDestEndpointId(getDestEndpointId());
        indicator.setComponentId(getComponentId());
        indicator.setEntityId(getEntityId());
        return indicator;
    }

    @Override public int remoteHashCode() {
        int result = 17;
        result = 31 * result + sourceEndpointId;
        result = 31 * result + destEndpointId;
        result = 31 * result + componentId;
        return result;
    }

    @Override public void deserialize(RemoteData remoteData) {
        setSourceEndpointId(remoteData.getDataIntegers(0));
        setDestEndpointId(remoteData.getDataIntegers(1));
        setComponentId(remoteData.getDataIntegers(2));

        setTimeBucket(remoteData.getDataLongs(0));

        setEntityId(remoteData.getDataStrings(0));
    }

    @Override public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();

        remoteBuilder.addDataIntegers(getSourceEndpointId());
        remoteBuilder.addDataIntegers(getDestEndpointId());
        remoteBuilder.addDataIntegers(getComponentId());

        remoteBuilder.addDataLongs(getTimeBucket());

        remoteBuilder.addDataStrings(getEntityId());
        return remoteBuilder;
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + sourceEndpointId;
        result = 31 * result + destEndpointId;
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

        EndpointRelationServerSideIndicator indicator = (EndpointRelationServerSideIndicator)obj;
        if (sourceEndpointId != indicator.sourceEndpointId)
            return false;
        if (destEndpointId != indicator.destEndpointId)
            return false;
        if (componentId != indicator.componentId)
            return false;

        if (getTimeBucket() != indicator.getTimeBucket())
            return false;

        return true;
    }

    public static class Builder implements StorageBuilder<EndpointRelationServerSideIndicator> {

        @Override public EndpointRelationServerSideIndicator map2Data(Map<String, Object> dbMap) {
            EndpointRelationServerSideIndicator indicator = new EndpointRelationServerSideIndicator();
            indicator.setSourceEndpointId(((Number)dbMap.get(SOURCE_ENDPOINT_ID)).intValue());
            indicator.setDestEndpointId(((Number)dbMap.get(DEST_ENDPOINT_ID)).intValue());
            indicator.setComponentId(((Number)dbMap.get(COMPONENT_ID)).intValue());
            indicator.setTimeBucket(((Number)dbMap.get(TIME_BUCKET)).longValue());
            indicator.setEntityId((String)dbMap.get(ENTITY_ID));
            return indicator;
        }

        @Override public Map<String, Object> data2Map(EndpointRelationServerSideIndicator storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put(SOURCE_ENDPOINT_ID, storageData.getSourceEndpointId());
            map.put(DEST_ENDPOINT_ID, storageData.getDestEndpointId());
            map.put(COMPONENT_ID, storageData.getComponentId());
            map.put(TIME_BUCKET, storageData.getTimeBucket());
            map.put(ENTITY_ID, storageData.getEntityId());
            return map;
        }
    }
}
