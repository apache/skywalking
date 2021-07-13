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

package org.apache.skywalking.oap.server.core.analysis.manual.relation.endpoint;

import java.util.HashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

@Stream(name = EndpointRelationServerSideMetrics.INDEX_NAME, scopeId = DefaultScopeDefine.ENDPOINT_RELATION,
    builder = EndpointRelationServerSideMetrics.Builder.class, processor = MetricsStreamProcessor.class)
@EqualsAndHashCode(of = {
    "entityId"
}, callSuper = true)
public class EndpointRelationServerSideMetrics extends Metrics {

    public static final String INDEX_NAME = "endpoint_relation_server_side";
    public static final String SOURCE_ENDPOINT = "source_endpoint";
    public static final String DEST_ENDPOINT = "dest_endpoint";
    public static final String COMPONENT_ID = "component_id";

    @Setter
    @Getter
    @Column(columnName = SOURCE_ENDPOINT)
    private String sourceEndpoint;
    @Setter
    @Getter
    @Column(columnName = DEST_ENDPOINT)
    private String destEndpoint;
    @Setter
    @Getter
    @Column(columnName = COMPONENT_ID, storageOnly = true)
    private int componentId;
    @Setter
    @Getter
    @Column(columnName = ENTITY_ID, length = 512)
    private String entityId;

    @Override
    protected String id0() {
        String splitJointId = String.valueOf(getTimeBucket());
        splitJointId += Const.ID_CONNECTOR + entityId;
        return splitJointId;
    }

    @Override
    public boolean combine(Metrics metrics) {
        return true;
    }

    @Override
    public void calculate() {

    }

    @Override
    public Metrics toHour() {
        EndpointRelationServerSideMetrics metrics = new EndpointRelationServerSideMetrics();
        metrics.setTimeBucket(toTimeBucketInHour());
        metrics.setSourceEndpoint(getSourceEndpoint());
        metrics.setDestEndpoint(getDestEndpoint());
        metrics.setComponentId(getComponentId());
        metrics.setEntityId(getEntityId());
        return metrics;
    }

    @Override
    public Metrics toDay() {
        EndpointRelationServerSideMetrics metrics = new EndpointRelationServerSideMetrics();
        metrics.setTimeBucket(toTimeBucketInDay());
        metrics.setSourceEndpoint(getSourceEndpoint());
        metrics.setDestEndpoint(getDestEndpoint());
        metrics.setComponentId(getComponentId());
        metrics.setEntityId(getEntityId());
        return metrics;
    }

    @Override
    public int remoteHashCode() {
        int result = 17;
        result = 31 * result + entityId.hashCode();
        result = (int) (31 * result + getTimeBucket());
        return result;
    }

    @Override
    public void deserialize(RemoteData remoteData) {
        setComponentId(remoteData.getDataIntegers(0));

        setTimeBucket(remoteData.getDataLongs(0));

        setEntityId(remoteData.getDataStrings(0));
        setSourceEndpoint(remoteData.getDataStrings(1));
        setDestEndpoint(remoteData.getDataStrings(2));
    }

    @Override
    public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();

        remoteBuilder.addDataIntegers(getComponentId());

        remoteBuilder.addDataLongs(getTimeBucket());

        remoteBuilder.addDataStrings(getEntityId());
        remoteBuilder.addDataStrings(getSourceEndpoint());
        remoteBuilder.addDataStrings(getDestEndpoint());
        return remoteBuilder;
    }

    public static class Builder implements StorageHashMapBuilder<EndpointRelationServerSideMetrics> {

        @Override
        public EndpointRelationServerSideMetrics storage2Entity(Map<String, Object> dbMap) {
            EndpointRelationServerSideMetrics metrics = new EndpointRelationServerSideMetrics();
            metrics.setSourceEndpoint((String) dbMap.get(SOURCE_ENDPOINT));
            metrics.setDestEndpoint((String) dbMap.get(DEST_ENDPOINT));
            metrics.setComponentId(((Number) dbMap.get(COMPONENT_ID)).intValue());
            metrics.setTimeBucket(((Number) dbMap.get(TIME_BUCKET)).longValue());
            metrics.setEntityId((String) dbMap.get(ENTITY_ID));
            return metrics;
        }

        @Override
        public Map<String, Object> entity2Storage(EndpointRelationServerSideMetrics storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put(SOURCE_ENDPOINT, storageData.getSourceEndpoint());
            map.put(DEST_ENDPOINT, storageData.getDestEndpoint());
            map.put(COMPONENT_ID, storageData.getComponentId());
            map.put(TIME_BUCKET, storageData.getTimeBucket());
            map.put(ENTITY_ID, storageData.getEntityId());
            return map;
        }
    }
}
