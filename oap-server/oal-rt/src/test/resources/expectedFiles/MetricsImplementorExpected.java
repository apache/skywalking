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

package org.apache.skywalking.oap.server.core.analysis.generated.service.serviceavg;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.metrics.LongAvgMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.MetricsMetaInfo;
import org.apache.skywalking.oap.server.core.analysis.metrics.WithMetadata;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

/**
 * This class is auto generated. Please don't change this class manually.
 */
@Stream(name = "service_avg", scopeId = 1, builder = ServiceAvgMetrics.Builder.class, processor = MetricsStreamProcessor.class)
public class ServiceAvgMetrics extends LongAvgMetrics implements WithMetadata {

    @Setter
    @Getter
    @Column(columnName = "entity_id")
    private java.lang.String entityId;

    @Override
    public String id() {
        String splitJointId = String.valueOf(getTimeBucket());
        splitJointId += Const.ID_CONNECTOR + entityId;
        return splitJointId;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + entityId.hashCode();
        result = 31 * result + (int) getTimeBucket();
        return result;
    }

    @Override
    public int remoteHashCode() {
        int result = 17;
        result = 31 * result + entityId.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        ServiceAvgMetrics metrics = (ServiceAvgMetrics) obj;
        if (!entityId.equals(metrics.entityId))
            return false;

        if (getTimeBucket() != metrics.getTimeBucket())
            return false;

        return true;
    }

    @Override
    public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.addDataStrings(getEntityId());
        remoteBuilder.addDataStrings(getStringField());

        remoteBuilder.addDataLongs(getSummation());
        remoteBuilder.addDataLongs(getValue());
        remoteBuilder.addDataLongs(getTimeBucket());

        remoteBuilder.addDataIntegers(getCount());

        return remoteBuilder;
    }

    @Override
    public void deserialize(RemoteData remoteData) {
        setEntityId(remoteData.getDataStrings(0));
        setStringField(remoteData.getDataStrings(1));

        setSummation(remoteData.getDataLongs(0));
        setValue(remoteData.getDataLongs(1));
        setTimeBucket(remoteData.getDataLongs(2));

        setCount(remoteData.getDataIntegers(0));

    }

    @Override
    public MetricsMetaInfo getMeta() {
        return new MetricsMetaInfo("generate_metrics", 1, entityId);
    }

    @Override
    public Metrics toHour() {
        ServiceAvgMetrics metrics = new ServiceAvgMetrics();
        metrics.setEntityId(this.getEntityId());
        metrics.setSummation(this.getSummation());
        metrics.setCount(this.getCount());
        metrics.setValue(this.getValue());
        metrics.setTimeBucket(toTimeBucketInHour());
        metrics.setStringField(this.getStringField());
        return metrics;
    }

    @Override
    public Metrics toDay() {
        ServiceAvgMetrics metrics = new ServiceAvgMetrics();
        metrics.setEntityId(this.getEntityId());
        metrics.setSummation(this.getSummation());
        metrics.setCount(this.getCount());
        metrics.setValue(this.getValue());
        metrics.setTimeBucket(toTimeBucketInDay());
        metrics.setStringField(this.getStringField());
        return metrics;
    }

    @Override
    public Metrics toMonth() {
        ServiceAvgMetrics metrics = new ServiceAvgMetrics();
        metrics.setEntityId(this.getEntityId());
        metrics.setSummation(this.getSummation());
        metrics.setCount(this.getCount());
        metrics.setValue(this.getValue());
        metrics.setTimeBucket(toTimeBucketInMonth());
        metrics.setStringField(this.getStringField());
        return metrics;
    }

    public static class Builder implements StorageBuilder<ServiceAvgMetrics> {

        @Override
        public Map<String, Object> data2Map(ServiceAvgMetrics storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put("entity_id", storageData.getEntityId());
            map.put("summation", storageData.getSummation());
            map.put("count", storageData.getCount());
            map.put("value", storageData.getValue());
            map.put("time_bucket", storageData.getTimeBucket());
            map.put("string_field", storageData.getStringField());
            return map;
        }

        @Override
        public ServiceAvgMetrics map2Data(Map<String, Object> dbMap) {
            ServiceAvgMetrics metrics = new ServiceAvgMetrics();
            metrics.setEntityId((String) dbMap.get("entity_id"));
            metrics.setSummation(((Number) dbMap.get("summation")).longValue());
            metrics.setCount(((Number) dbMap.get("count")).intValue());
            metrics.setValue(((Number) dbMap.get("value")).longValue());
            metrics.setTimeBucket(((Number) dbMap.get("time_bucket")).longValue());
            metrics.setStringField((String) dbMap.get("string_field"));
            return metrics;
        }
    }
}