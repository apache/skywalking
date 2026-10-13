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
 */

package org.apache.skywalking.oap.server.core.analysis.meter.function.percent;

import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.meter.Meter;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.MeterFunction;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * The meter-system counterpart of OAL's {@code percent(condition)}, which
 * {@link org.apache.skywalking.oap.server.core.analysis.metrics.PercentMetrics} implements: the share of the
 * observations that matched, in ten-thousandths, so a TAL metric named like {@code service_sla} holds the same
 * number in the same unit as the agent metric.
 */
@MeterFunction(functionName = "percent")
@ToString
public abstract class PercentFunction extends Meter implements AcceptableValue<Boolean>, IntValueHolder {
    protected static final String TOTAL = "total";
    protected static final String MATCH = "match";
    protected static final String PERCENTAGE = "percentage";

    @Setter
    @Getter
    @ElasticSearch.EnableDocValues
    @Column(name = ENTITY_ID, length = 512)
    @BanyanDB.SeriesID(index = 0)
    private String entityId;

    /**
     * Service ID is required for sort query.
     */
    @Setter
    @Getter
    @Column(name = InstanceTraffic.SERVICE_ID)
    private String serviceId;

    @Getter
    @Setter
    @Column(name = TOTAL, storageOnly = true)
    @BanyanDB.MeasureField
    protected long total;
    @Getter
    @Setter
    @Column(name = MATCH, storageOnly = true)
    @BanyanDB.MeasureField
    protected long match;
    @Getter
    @Setter
    @ElasticSearch.EnableDocValues
    @Column(name = PERCENTAGE, dataType = Column.ValueDataType.COMMON_VALUE)
    @BanyanDB.MeasureField
    private int percentage;

    @Override
    public final boolean combine(Metrics metrics) {
        PercentFunction percentFunction = (PercentFunction) metrics;
        this.total += percentFunction.total;
        this.match += percentFunction.match;
        return true;
    }

    @Override
    public final void calculate() {
        this.percentage = (int) (match * 10000 / total);
    }

    @Override
    public int getValue() {
        return percentage;
    }

    @Override
    public Metrics toHour() {
        PercentFunction metrics = (PercentFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInHour());
        metrics.setServiceId(getServiceId());
        metrics.setTotal(getTotal());
        metrics.setMatch(getMatch());

        metrics.setAttr0(getAttr0());
        metrics.setAttr1(getAttr1());
        metrics.setAttr2(getAttr2());
        metrics.setAttr3(getAttr3());
        metrics.setAttr4(getAttr4());
        metrics.setAttr5(getAttr5());
        return metrics;
    }

    @Override
    public Metrics toDay() {
        PercentFunction metrics = (PercentFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInDay());
        metrics.setServiceId(getServiceId());
        metrics.setTotal(getTotal());
        metrics.setMatch(getMatch());

        metrics.setAttr0(getAttr0());
        metrics.setAttr1(getAttr1());
        metrics.setAttr2(getAttr2());
        metrics.setAttr3(getAttr3());
        metrics.setAttr4(getAttr4());
        metrics.setAttr5(getAttr5());
        return metrics;
    }

    @Override
    public int remoteHashCode() {
        return entityId.hashCode();
    }

    @Override
    public void deserialize(final RemoteData remoteData) {
        this.total = remoteData.getDataLongs(0);
        this.match = remoteData.getDataLongs(1);
        setTimeBucket(remoteData.getDataLongs(2));

        this.entityId = remoteData.getDataStrings(0);
        this.serviceId = remoteData.getDataStrings(1);

        if (StringUtil.isNotEmpty(remoteData.getDataStrings(2))) {
            setAttr0(remoteData.getDataStrings(2));
        }

        if (StringUtil.isNotEmpty(remoteData.getDataStrings(3))) {
            setAttr1(remoteData.getDataStrings(3));
        }

        if (StringUtil.isNotEmpty(remoteData.getDataStrings(4))) {
            setAttr2(remoteData.getDataStrings(4));
        }

        if (StringUtil.isNotEmpty(remoteData.getDataStrings(5))) {
            setAttr3(remoteData.getDataStrings(5));
        }

        if (StringUtil.isNotEmpty(remoteData.getDataStrings(6))) {
            setAttr4(remoteData.getDataStrings(6));
        }

        if (StringUtil.isNotEmpty(remoteData.getDataStrings(7))) {
            setAttr5(remoteData.getDataStrings(7));
        }
    }

    @Override
    public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.addDataLongs(total);
        remoteBuilder.addDataLongs(match);
        remoteBuilder.addDataLongs(getTimeBucket());

        remoteBuilder.addDataStrings(entityId);
        remoteBuilder.addDataStrings(serviceId);

        remoteBuilder.addDataStrings(getAttr0() == null ? Const.EMPTY_STRING : getAttr0());
        remoteBuilder.addDataStrings(getAttr1() == null ? Const.EMPTY_STRING : getAttr1());
        remoteBuilder.addDataStrings(getAttr2() == null ? Const.EMPTY_STRING : getAttr2());
        remoteBuilder.addDataStrings(getAttr3() == null ? Const.EMPTY_STRING : getAttr3());
        remoteBuilder.addDataStrings(getAttr4() == null ? Const.EMPTY_STRING : getAttr4());
        remoteBuilder.addDataStrings(getAttr5() == null ? Const.EMPTY_STRING : getAttr5());
        return remoteBuilder;
    }

    @Override
    protected StorageID id0() {
        return new StorageID()
            .append(TIME_BUCKET, getTimeBucket())
            .append(ENTITY_ID, getEntityId());
    }

    @Override
    public void accept(final MeterEntity entity, final Boolean isMatch) {
        decorate(entity);
        this.entityId = entity.id();
        this.serviceId = entity.serviceId();
        this.total++;
        if (isMatch != null && isMatch) {
            this.match++;
        }
    }

    @Override
    public Class<? extends StorageBuilder> builder() {
        return PercentStorageBuilder.class;
    }

    public static class PercentStorageBuilder implements StorageBuilder<PercentFunction> {
        @Override
        public PercentFunction storage2Entity(final Convert2Entity converter) {
            PercentFunction metrics = new PercentFunction() {
                @Override
                public AcceptableValue<Boolean> createNew() {
                    throw new UnexpectedException("createNew should not be called");
                }
            };
            metrics.setTotal(((Number) converter.get(TOTAL)).longValue());
            metrics.setMatch(((Number) converter.get(MATCH)).longValue());
            metrics.setPercentage(((Number) converter.get(PERCENTAGE)).intValue());
            metrics.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            metrics.setServiceId((String) converter.get(InstanceTraffic.SERVICE_ID));
            metrics.setEntityId((String) converter.get(ENTITY_ID));

            metrics.setAttr0((String) converter.get(ATTR0));
            metrics.setAttr1((String) converter.get(ATTR1));
            metrics.setAttr2((String) converter.get(ATTR2));
            metrics.setAttr3((String) converter.get(ATTR3));
            metrics.setAttr4((String) converter.get(ATTR4));
            metrics.setAttr5((String) converter.get(ATTR5));
            return metrics;
        }

        @Override
        public void entity2Storage(final PercentFunction storageData, final Convert2Storage converter) {
            converter.accept(TOTAL, storageData.getTotal());
            converter.accept(MATCH, storageData.getMatch());
            converter.accept(PERCENTAGE, storageData.getPercentage());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
            converter.accept(InstanceTraffic.SERVICE_ID, storageData.getServiceId());
            converter.accept(ENTITY_ID, storageData.getEntityId());

            converter.accept(ATTR0, storageData.getAttr0());
            converter.accept(ATTR1, storageData.getAttr1());
            converter.accept(ATTR2, storageData.getAttr2());
            converter.accept(ATTR3, storageData.getAttr3());
            converter.accept(ATTR4, storageData.getAttr4());
            converter.accept(ATTR5, storageData.getAttr5());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PercentFunction)) {
            return false;
        }
        PercentFunction function = (PercentFunction) o;
        return Objects.equals(entityId, function.entityId) &&
            getTimeBucket() == function.getTimeBucket();
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId, getTimeBucket());
    }
}
