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
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.*;

@IndicatorType
@StreamData
@StorageEntity(name = EndpointRelationClientSideIndicator.INDEX_NAME, builder = EndpointRelationClientSideIndicator.Builder.class)
public class EndpointRelationClientSideIndicator extends Indicator {

    public static final String INDEX_NAME = "endpoint_relation_client_side";
    public static final String SOURCE_ENDPOINT_ID = "source_endpoint_id";
    public static final String DEST_ENDPOINT_ID = "dest_endpoint_id";

    @Setter @Getter @Column(columnName = SOURCE_ENDPOINT_ID) @IDColumn private int sourceEndpointId;
    @Setter @Getter @Column(columnName = DEST_ENDPOINT_ID) @IDColumn private int destEndpointId;

    @Override public String id() {
        String splitJointId = String.valueOf(getTimeBucket());
        splitJointId += Const.ID_SPLIT + String.valueOf(sourceEndpointId);
        splitJointId += Const.ID_SPLIT + String.valueOf(destEndpointId);
        return splitJointId;
    }

    @Override public void combine(Indicator indicator) {

    }

    @Override public void calculate() {

    }

    @Override public Indicator toHour() {
        EndpointRelationClientSideIndicator indicator = new EndpointRelationClientSideIndicator();
        indicator.setTimeBucket(toTimeBucketInHour());
        indicator.setSourceEndpointId(getSourceEndpointId());
        indicator.setDestEndpointId(getDestEndpointId());
        return indicator;
    }

    @Override public Indicator toDay() {
        EndpointRelationClientSideIndicator indicator = new EndpointRelationClientSideIndicator();
        indicator.setTimeBucket(toTimeBucketInDay());
        indicator.setSourceEndpointId(getSourceEndpointId());
        indicator.setDestEndpointId(getDestEndpointId());
        return indicator;
    }

    @Override public Indicator toMonth() {
        EndpointRelationClientSideIndicator indicator = new EndpointRelationClientSideIndicator();
        indicator.setTimeBucket(toTimeBucketInMonth());
        indicator.setSourceEndpointId(getSourceEndpointId());
        indicator.setDestEndpointId(getDestEndpointId());
        return indicator;
    }

    @Override public int remoteHashCode() {
        int result = 17;
        result = 31 * result + sourceEndpointId;
        result = 31 * result + destEndpointId;
        return result;
    }

    @Override public void deserialize(RemoteData remoteData) {
        setSourceEndpointId(remoteData.getDataIntegers(0));
        setDestEndpointId(remoteData.getDataIntegers(1));
        setTimeBucket(remoteData.getDataLongs(0));
    }

    @Override public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();

        remoteBuilder.setDataIntegers(1, getDestEndpointId());
        remoteBuilder.setDataIntegers(0, getSourceEndpointId());
        remoteBuilder.setDataLongs(0, getTimeBucket());

        return remoteBuilder;
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + sourceEndpointId;
        result = 31 * result + destEndpointId;
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

        EndpointRelationClientSideIndicator indicator = (EndpointRelationClientSideIndicator)obj;
        if (sourceEndpointId != indicator.sourceEndpointId)
            return false;
        if (destEndpointId != indicator.destEndpointId)
            return false;

        if (getTimeBucket() != indicator.getTimeBucket())
            return false;

        return true;
    }

    public static class Builder implements StorageBuilder<EndpointRelationClientSideIndicator> {

        @Override public EndpointRelationClientSideIndicator map2Data(Map<String, Object> dbMap) {
            EndpointRelationClientSideIndicator indicator = new EndpointRelationClientSideIndicator();
            indicator.setSourceEndpointId(((Number)dbMap.get(SOURCE_ENDPOINT_ID)).intValue());
            indicator.setDestEndpointId(((Number)dbMap.get(DEST_ENDPOINT_ID)).intValue());
            indicator.setTimeBucket(((Number)dbMap.get(TIME_BUCKET)).longValue());
            return indicator;
        }

        @Override public Map<String, Object> data2Map(EndpointRelationClientSideIndicator storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put(TIME_BUCKET, storageData.getTimeBucket());
            map.put(SOURCE_ENDPOINT_ID, storageData.getSourceEndpointId());
            map.put(DEST_ENDPOINT_ID, storageData.getDestEndpointId());
            return map;
        }
    }
}
