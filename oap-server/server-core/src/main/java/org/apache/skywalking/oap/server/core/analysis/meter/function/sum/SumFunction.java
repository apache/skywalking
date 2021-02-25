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

package org.apache.skywalking.oap.server.core.analysis.meter.function.sum;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.MeterFunction;
import org.apache.skywalking.oap.server.core.analysis.metrics.LongValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Entrance;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.SourceFrom;
import org.apache.skywalking.oap.server.core.query.sql.Function;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

@ToString
@MeterFunction(functionName = "sum")
public abstract class SumFunction extends Metrics implements AcceptableValue<Long>, LongValueHolder {
    protected static final String VALUE = "value";

    @Setter
    @Getter
    @Column(columnName = ENTITY_ID, length = 512)
    private String entityId;

    @Setter
    @Getter
    @Column(columnName = InstanceTraffic.SERVICE_ID)
    private String serviceId;

    @Getter
    @Setter
    @Column(columnName = VALUE, dataType = Column.ValueDataType.COMMON_VALUE, function = Function.Sum)
    private long value;

    @Entrance
    public final void combine(@SourceFrom long value) {
        setValue(this.value + value);
    }

    @Override
    public final boolean combine(Metrics metrics) {
        final SumFunction sumFunc = (SumFunction) metrics;
        combine(sumFunc.getValue());
        return true;
    }

    @Override
    public final void calculate() {
    }

    @Override
    public Metrics toHour() {
        final SumFunction metrics = (SumFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInHour());
        metrics.setServiceId(getServiceId());
        metrics.setValue(getValue());
        return metrics;
    }

    @Override
    public Metrics toDay() {
        final SumFunction metrics = (SumFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInDay());
        metrics.setServiceId(getServiceId());
        metrics.setValue(getValue());
        return metrics;
    }

    @Override
    public int remoteHashCode() {
        return getEntityId().hashCode();
    }

    @Override
    public void deserialize(final RemoteData remoteData) {
        setValue(remoteData.getDataLongs(0));
        setTimeBucket(remoteData.getDataLongs(1));

        setEntityId(remoteData.getDataStrings(0));
        setServiceId(remoteData.getDataStrings(1));
    }

    @Override
    public RemoteData.Builder serialize() {
        final RemoteData.Builder remoteBuilder = RemoteData.newBuilder();

        remoteBuilder.addDataLongs(getValue());
        remoteBuilder.addDataLongs(getTimeBucket());

        remoteBuilder.addDataStrings(getEntityId());
        remoteBuilder.addDataStrings(getServiceId());

        return remoteBuilder;
    }

    @Override
    public String id() {
        return getTimeBucket() + Const.ID_CONNECTOR + getEntityId();
    }

    @Override
    public void accept(final MeterEntity entity, final Long value) {
        setEntityId(entity.id());
        setServiceId(entity.serviceId());
        setValue(getValue() + value);
    }

    @Override
    public Class<? extends StorageHashMapBuilder<?>> builder() {
        return SumStorageBuilder.class;
    }

    public static class SumStorageBuilder implements StorageHashMapBuilder<SumFunction> {
        @Override
        public SumFunction storage2Entity(final Map<String, Object> dbMap) {
            final SumFunction metrics = new SumFunction() {
                @Override
                public AcceptableValue<Long> createNew() {
                    throw new UnexpectedException("createNew should not be called");
                }
            };
            metrics.setValue(((Number) dbMap.get(VALUE)).longValue());
            metrics.setTimeBucket(((Number) dbMap.get(TIME_BUCKET)).longValue());
            metrics.setServiceId((String) dbMap.get(InstanceTraffic.SERVICE_ID));
            metrics.setEntityId((String) dbMap.get(ENTITY_ID));
            return metrics;
        }

        @Override
        public Map<String, Object> entity2Storage(final SumFunction storageData) {
            final Map<String, Object> map = new HashMap<>();
            map.put(VALUE, storageData.getValue());
            map.put(TIME_BUCKET, storageData.getTimeBucket());
            map.put(InstanceTraffic.SERVICE_ID, storageData.getServiceId());
            map.put(ENTITY_ID, storageData.getEntityId());
            return map;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SumFunction)) {
            return false;
        }
        final SumFunction function = (SumFunction) o;
        return Objects.equals(getEntityId(), function.getEntityId())
            && Objects.equals(getTimeBucket(), function.getTimeBucket());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getEntityId(), getTimeBucket());
    }
}
