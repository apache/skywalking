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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.metrics.CPMMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

@Stream(
    name = ServiceCpmMetrics.INDEX_NAME,
    scopeId = DefaultScopeDefine.SERVICE,
    builder = ServiceCpmMetricsBuilder.class,
    processor = MetricsStreamProcessor.class
)
@EqualsAndHashCode(of = {
    "entityId"
}, callSuper = true)
public class ServiceCpmMetrics extends CPMMetrics {
    public static final String INDEX_NAME = "service_cpm";

    @Setter
    @Getter
    @Column(
        name = "entity_id",
        length = 512
    )
    private String entityId;

    @Override
    protected StorageID id0() {
        return new StorageID()
            .append(TIME_BUCKET, getTimeBucket())
            .append(ENTITY_ID, entityId);
    }

    @Override
    public int remoteHashCode() {
        byte var1 = 17;
        int var2 = 31 * var1 + this.entityId.hashCode();
        return var2;
    }

    @Override
    public RemoteData.Builder serialize() {
        RemoteData.Builder var1 = RemoteData.newBuilder();
        var1.addDataStrings(this.getEntityId());
        var1.addDataLongs(this.getValue());
        var1.addDataLongs(this.getTotal());
        var1.addDataLongs(this.getTimeBucket());
        return var1;
    }

    @Override
    public void deserialize(RemoteData var1) {
        this.setEntityId(var1.getDataStrings(0));
        this.setValue(var1.getDataLongs(0));
        this.setTotal(var1.getDataLongs(1));
        this.setTimeBucket(var1.getDataLongs(2));
    }

    @Override
    public Metrics toHour() {
        ServiceCpmMetrics var1 = new ServiceCpmMetrics();
        var1.setEntityId(this.getEntityId());
        var1.setValue(this.getValue());
        var1.setTotal(this.getTotal());
        var1.setTimeBucket(this.toTimeBucketInHour());
        return var1;
    }

    @Override
    public Metrics toDay() {
        ServiceCpmMetrics var1 = new ServiceCpmMetrics();
        var1.setEntityId(this.getEntityId());
        var1.setValue(this.getValue());
        var1.setTotal(this.getTotal());
        var1.setTimeBucket(this.toTimeBucketInDay());
        return var1;
    }
}
