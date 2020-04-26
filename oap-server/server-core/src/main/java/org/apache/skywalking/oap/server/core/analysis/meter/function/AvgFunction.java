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

package org.apache.skywalking.oap.server.core.analysis.meter.function;

import java.util.HashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.metrics.LongAvgMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

@MeterFunction(functionName = "avg")
@EqualsAndHashCode(of = {
    "entityId",
    "timeBucket"
})
public abstract class AvgFunction extends LongAvgMetrics implements AcceptableValue<Long> {
    @Setter
    @Getter
    @Column(columnName = ENTITY_ID)
    private String entityId;
    /**
     * Service ID is required for sort query.
     */
    @Setter
    @Getter
    @Column(columnName = InstanceTraffic.SERVICE_ID)
    private String serviceId;

    @Override
    public Metrics toHour() {
        AvgFunction metrics = (AvgFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInHour());
        metrics.setServiceId(getServiceId());
        metrics.setSummation(getSummation());
        metrics.setCount(getCount());
        return metrics;
    }

    @Override
    public Metrics toDay() {
        AvgFunction metrics = (AvgFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInDay());
        metrics.setServiceId(getServiceId());
        metrics.setSummation(getSummation());
        metrics.setCount(getCount());
        return metrics;
    }

    @Override
    public int remoteHashCode() {
        return entityId.hashCode();
    }

    @Override
    public void deserialize(final RemoteData remoteData) {
        this.count = remoteData.getDataLongs(0);
        this.summation = remoteData.getDataLongs(1);
        setTimeBucket(remoteData.getDataLongs(2));

        this.entityId = remoteData.getDataStrings(0);
        this.serviceId = remoteData.getDataStrings(1);
    }

    @Override
    public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.addDataLongs(count);
        remoteBuilder.addDataLongs(summation);
        remoteBuilder.addDataLongs(getTimeBucket());

        remoteBuilder.addDataStrings(entityId);
        remoteBuilder.addDataStrings(serviceId);

        return remoteBuilder;
    }

    @Override
    public String id() {
        return getTimeBucket() + Const.ID_CONNECTOR + entityId;
    }

    @Override
    public void accept(final MeterEntity entity, final Long value) {
        this.entityId = entity.id();
        this.serviceId = entity.serviceId();
        this.summation += value;
        this.count += 1;
    }

    @Override
    public Class<? extends StorageBuilder> builder() {
        return AvgStorageBuilder.class;
    }

    public static class AvgStorageBuilder implements StorageBuilder<AvgFunction> {
        @Override
        public AvgFunction map2Data(final Map<String, Object> dbMap) {
            AvgFunction metrics = new AvgFunction() {
                @Override
                public AcceptableValue<Long> createNew() {
                    throw new UnexpectedException("createNew should not be called");
                }
            };
            metrics.setSummation(((Number) dbMap.get(SUMMATION)).longValue());
            metrics.setValue(((Number) dbMap.get(VALUE)).longValue());
            metrics.setCount(((Number) dbMap.get(COUNT)).longValue());
            metrics.setTimeBucket(((Number) dbMap.get(TIME_BUCKET)).longValue());
            metrics.setServiceId((String) dbMap.get(InstanceTraffic.SERVICE_ID));
            metrics.setEntityId((String) dbMap.get(ENTITY_ID));
            return metrics;
        }

        @Override
        public Map<String, Object> data2Map(final AvgFunction storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put(SUMMATION, storageData.getSummation());
            map.put(VALUE, storageData.getValue());
            map.put(COUNT, storageData.getCount());
            map.put(TIME_BUCKET, storageData.getTimeBucket());
            map.put(InstanceTraffic.SERVICE_ID, storageData.getServiceId());
            map.put(ENTITY_ID, storageData.getEntityId());
            return map;
        }
    }
}
