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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

/**
 * Histogram includes data range buckets and the amount matched/grouped in the buckets.
 * This is for original histogram graph visualization
 */
@MeterFunction(functionName = "histogram")
@Slf4j
@EqualsAndHashCode(of = {
    "entityId",
    "timeBucket"
})
public abstract class HistogramFunction extends Metrics implements AcceptableValue<BucketedValues> {
    public static final String DATASET = "dataset";

    @Setter
    @Getter
    @Column(columnName = ENTITY_ID)
    private String entityId;
    @Getter
    @Setter
    @Column(columnName = DATASET, dataType = Column.ValueDataType.HISTOGRAM, storageOnly = true, defaultValue = 0)
    private DataTable dataset = new DataTable(30);
    /**
     * Service ID is required for sort query.
     */
    @Setter
    @Getter
    @Column(columnName = InstanceTraffic.SERVICE_ID)
    private String serviceId;

    @Override
    public void accept(final MeterEntity entity, final BucketedValues value) {
        if (dataset.size() > 0) {
            if (!value.isCompatible(dataset)) {
                throw new IllegalArgumentException(
                    "Incompatible BucketedValues [" + value + "] for current HistogramFunction[" + dataset + "]");
            }
        }

        this.entityId = entity.id();
        this.serviceId = entity.serviceId();

        final long[] values = value.getValues();
        for (int i = 0; i < values.length; i++) {
            final long bucket = value.getBuckets()[i];
            final long bucketValue = values[i];
            dataset.valueAccumulation(String.valueOf(bucket), bucketValue);
        }
    }

    @Override
    public void combine(final Metrics metrics) {
        final long[] existedBuckets = dataset.keys().stream().mapToLong(Long::parseLong).sorted().toArray();

        HistogramFunction histogram = (HistogramFunction) metrics;
        final long[] buckets2 = dataset.keys().stream().mapToLong(Long::parseLong).sorted().toArray();
        if (!Arrays.equals(existedBuckets, buckets2)) {
            log.warn("Incompatible BucketedValues [{}}] for current HistogramFunction[{}], metrics: {}, entity {}",
                     buckets2, existedBuckets, this.getClass().getName(), entityId
            );
            return;
        }
        this.dataset.append(histogram.dataset);
    }

    @Override
    public void calculate() {

    }

    @Override
    public Metrics toHour() {
        HistogramFunction metrics = (HistogramFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInHour());
        metrics.setServiceId(getServiceId());
        metrics.setDataset(getDataset());
        return metrics;
    }

    @Override
    public Metrics toDay() {
        HistogramFunction metrics = (HistogramFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInDay());
        metrics.setServiceId(getServiceId());
        metrics.setDataset(getDataset());
        return metrics;
    }

    @Override
    public int remoteHashCode() {
        return entityId.hashCode();
    }

    @Override
    public void deserialize(final RemoteData remoteData) {
        this.setTimeBucket(remoteData.getDataLongs(0));

        this.setEntityId(remoteData.getDataStrings(0));
        this.setServiceId(remoteData.getDataStrings(1));

        this.setDataset(new DataTable(remoteData.getDataTableStrings(0)));
    }

    @Override
    public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.addDataLongs(getTimeBucket());

        remoteBuilder.addDataStrings(entityId);
        remoteBuilder.addDataStrings(serviceId);

        remoteBuilder.addDataTableStrings(dataset.toStorageData());

        return remoteBuilder;
    }

    @Override
    public String id() {
        return getTimeBucket() + Const.ID_CONNECTOR + entityId;
    }

    @Override
    public Class<? extends StorageBuilder> builder() {
        return HistogramFunctionBuilder.class;
    }

    public static class HistogramFunctionBuilder implements StorageBuilder<HistogramFunction> {

        @Override
        public HistogramFunction map2Data(final Map<String, Object> dbMap) {
            HistogramFunction metrics = new HistogramFunction() {
                @Override
                public AcceptableValue<BucketedValues> createNew() {
                    throw new UnexpectedException("createNew should not be called");
                }
            };
            metrics.setDataset(new DataTable((String) dbMap.get(DATASET)));
            metrics.setTimeBucket(((Number) dbMap.get(TIME_BUCKET)).longValue());
            metrics.setServiceId((String) dbMap.get(InstanceTraffic.SERVICE_ID));
            metrics.setEntityId((String) dbMap.get(ENTITY_ID));
            return metrics;
        }

        @Override
        public Map<String, Object> data2Map(final HistogramFunction storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put(DATASET, storageData.getDataset());
            map.put(TIME_BUCKET, storageData.getTimeBucket());
            map.put(InstanceTraffic.SERVICE_ID, storageData.getServiceId());
            map.put(ENTITY_ID, storageData.getEntityId());
            return map;
        }
    }
}
