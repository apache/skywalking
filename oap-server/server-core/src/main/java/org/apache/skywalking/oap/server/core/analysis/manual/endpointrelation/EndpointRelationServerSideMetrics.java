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
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.*;

@Stream(name = EndpointRelationServerSideMetrics.INDEX_NAME, scopeId = DefaultScopeDefine.ENDPOINT_RELATION, storage = @Storage(builder = EndpointRelationServerSideMetrics.Builder.class), processor = MetricsStreamProcessor.class)
public class EndpointRelationServerSideMetrics extends Metrics {

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
        splitJointId += Const.ID_SPLIT + sourceEndpointId;
        splitJointId += Const.ID_SPLIT + destEndpointId;
        splitJointId += Const.ID_SPLIT + componentId;
        return splitJointId;
    }

    @Override public void combine(Metrics metrics) {

    }

    @Override public void calculate() {

    }

    @Override public Metrics toHour() {
        EndpointRelationServerSideMetrics metrics = new EndpointRelationServerSideMetrics();
        metrics.setTimeBucket(toTimeBucketInHour());
        metrics.setSourceEndpointId(getSourceEndpointId());
        metrics.setDestEndpointId(getDestEndpointId());
        metrics.setComponentId(getComponentId());
        metrics.setEntityId(getEntityId());
        return metrics;
    }

    @Override public Metrics toDay() {
        EndpointRelationServerSideMetrics metrics = new EndpointRelationServerSideMetrics();
        metrics.setTimeBucket(toTimeBucketInDay());
        metrics.setSourceEndpointId(getSourceEndpointId());
        metrics.setDestEndpointId(getDestEndpointId());
        metrics.setComponentId(getComponentId());
        metrics.setEntityId(getEntityId());
        return metrics;
    }

    @Override public Metrics toMonth() {
        EndpointRelationServerSideMetrics metrics = new EndpointRelationServerSideMetrics();
        metrics.setTimeBucket(toTimeBucketInMonth());
        metrics.setSourceEndpointId(getSourceEndpointId());
        metrics.setDestEndpointId(getDestEndpointId());
        metrics.setComponentId(getComponentId());
        metrics.setEntityId(getEntityId());
        return metrics;
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

        EndpointRelationServerSideMetrics metrics = (EndpointRelationServerSideMetrics)obj;
        if (sourceEndpointId != metrics.sourceEndpointId)
            return false;
        if (destEndpointId != metrics.destEndpointId)
            return false;
        if (componentId != metrics.componentId)
            return false;

        if (getTimeBucket() != metrics.getTimeBucket())
            return false;

        return true;
    }

    public static class Builder implements StorageBuilder<EndpointRelationServerSideMetrics> {

        @Override public EndpointRelationServerSideMetrics map2Data(Map<String, Object> dbMap) {
            EndpointRelationServerSideMetrics metrics = new EndpointRelationServerSideMetrics();
            metrics.setSourceEndpointId(((Number)dbMap.get(SOURCE_ENDPOINT_ID)).intValue());
            metrics.setDestEndpointId(((Number)dbMap.get(DEST_ENDPOINT_ID)).intValue());
            metrics.setComponentId(((Number)dbMap.get(COMPONENT_ID)).intValue());
            metrics.setTimeBucket(((Number)dbMap.get(TIME_BUCKET)).longValue());
            metrics.setEntityId((String)dbMap.get(ENTITY_ID));
            return metrics;
        }

        @Override public Map<String, Object> data2Map(EndpointRelationServerSideMetrics storageData) {
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
