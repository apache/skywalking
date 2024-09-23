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

package org.apache.skywalking.oap.server.core.analysis.manual.endpoint;

import com.google.common.base.Strings;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.MetricsExtension;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

@Stream(name = EndpointTraffic.INDEX_NAME, scopeId = DefaultScopeDefine.ENDPOINT,
    builder = EndpointTraffic.Builder.class, processor = MetricsStreamProcessor.class)
@MetricsExtension(supportDownSampling = false, supportUpdate = false)
@EqualsAndHashCode
public class EndpointTraffic extends Metrics {

    public static final String INDEX_NAME = "endpoint_traffic";

    public static final String SERVICE_ID = "service_id";
    public static final String NAME = "endpoint_traffic_name";

    @Setter
    @Getter
    @Column(name = SERVICE_ID)
    @BanyanDB.SeriesID(index = 0)
    private String serviceId;
    @Setter
    @Getter
    @Column(name = NAME)
    @ElasticSearch.Column(legacyName = "name")
    @ElasticSearch.MatchQuery
    @BanyanDB.MatchQuery
    @BanyanDB.SeriesID(index = 1)
    private String name = Const.EMPTY_STRING;

    @Override
    protected StorageID id0() {
        // Downgrade the time bucket to day level only.
        // supportDownSampling == false for this entity.
        return new StorageID()
            .appendMutant(
                new String[] {
                    SERVICE_ID,
                    NAME
                },
                IDManager.EndpointID.buildId(
                    this.getServiceId(), this.getName())
            );
    }

    @Override
    public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.addDataLongs(getTimeBucket());

        remoteBuilder.addDataStrings(serviceId);
        remoteBuilder.addDataStrings(Strings.isNullOrEmpty(name) ? Const.EMPTY_STRING : name);
        return remoteBuilder;
    }

    @Override
    public void deserialize(RemoteData remoteData) {
        setTimeBucket(remoteData.getDataLongs(0));

        setServiceId(remoteData.getDataStrings(0));
        setName(remoteData.getDataStrings(1));
    }

    @Override
    public int remoteHashCode() {
        return hashCode();
    }

    public static class Builder implements StorageBuilder<EndpointTraffic> {
        @Override
        public EndpointTraffic storage2Entity(final Convert2Entity converter) {
            EndpointTraffic inventory = new EndpointTraffic();
            inventory.setServiceId((String) converter.get(SERVICE_ID));
            inventory.setName((String) converter.get(NAME));
            inventory.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            return inventory;
        }

        @Override
        public void entity2Storage(final EndpointTraffic storageData, final Convert2Storage converter) {
            converter.accept(SERVICE_ID, storageData.getServiceId());
            converter.accept(NAME, storageData.getName());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
        }
    }

    @Override
    public boolean combine(final Metrics metrics) {
        return true;
    }

    @Override
    public void calculate() {

    }

    @Override
    public Metrics toHour() {
        return null;
    }

    @Override
    public Metrics toDay() {
        return null;
    }
}
