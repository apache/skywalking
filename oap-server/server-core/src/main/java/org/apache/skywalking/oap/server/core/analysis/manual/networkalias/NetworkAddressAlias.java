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

package org.apache.skywalking.oap.server.core.analysis.manual.networkalias;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.MetricsExtension;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.ShardingAlgorithm;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.SQLDatabase;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.NETWORK_ADDRESS_ALIAS;

@ScopeDeclaration(id = NETWORK_ADDRESS_ALIAS, name = "NetworkAddressAlias")
@Stream(name = NetworkAddressAlias.INDEX_NAME, scopeId = NETWORK_ADDRESS_ALIAS,
    builder = NetworkAddressAlias.Builder.class, processor = MetricsStreamProcessor.class)
@MetricsExtension(supportDownSampling = false, supportUpdate = true)
@EqualsAndHashCode(of = {
    "address"
})
@SQLDatabase.Sharding(shardingAlgorithm = ShardingAlgorithm.NO_SHARDING)
public class NetworkAddressAlias extends Metrics {
    public static final String INDEX_NAME = "network_address_alias";
    public static final String ADDRESS = "address";
    public static final String REPRESENT_SERVICE_ID = "represent_service_id";
    public static final String REPRESENT_SERVICE_INSTANCE_ID = "represent_service_instance_id";
    public static final String LAST_UPDATE_TIME_BUCKET = "last_update_time_bucket";

    @Setter
    @Getter
    @Column(name = ADDRESS)
    @BanyanDB.SeriesID(index = 0)
    private String address;
    @Setter
    @Getter
    @Column(name = REPRESENT_SERVICE_ID)
    private String representServiceId;
    @Setter
    @Getter
    @Column(name = REPRESENT_SERVICE_INSTANCE_ID)
    private String representServiceInstanceId;
    @Setter
    @Getter
    @Column(name = LAST_UPDATE_TIME_BUCKET)
    private long lastUpdateTimeBucket;

    @Override
    public boolean combine(final Metrics metrics) {
        NetworkAddressAlias alias = (NetworkAddressAlias) metrics;
        this.representServiceId = alias.getRepresentServiceId();
        this.representServiceInstanceId = alias.getRepresentServiceInstanceId();
        this.lastUpdateTimeBucket = alias.getLastUpdateTimeBucket();
        /**
         * Keep the time bucket as the same time inserted.
         */
        if (this.getTimeBucket() > metrics.getTimeBucket()) {
            this.setTimeBucket(metrics.getTimeBucket());
        }
        return true;
    }

    @Override
    protected StorageID id0() {
        return new StorageID().appendMutant(
            new String[] {ADDRESS}, IDManager.NetworkAddressAliasDefine.buildId(address));
    }

    @Override
    public int remoteHashCode() {
        return this.hashCode();
    }

    @Override
    public void deserialize(final RemoteData remoteData) {
        setAddress(remoteData.getDataStrings(0));
        setRepresentServiceId(remoteData.getDataStrings(1));
        setRepresentServiceInstanceId(remoteData.getDataStrings(2));

        setLastUpdateTimeBucket(remoteData.getDataLongs(0));
        setTimeBucket(remoteData.getDataLongs(1));
    }

    @Override
    public RemoteData.Builder serialize() {
        final RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(address);
        builder.addDataStrings(representServiceId);
        builder.addDataStrings(representServiceInstanceId);

        builder.addDataLongs(lastUpdateTimeBucket);
        builder.addDataLongs(getTimeBucket());
        return builder;
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

    public static class Builder implements StorageBuilder<NetworkAddressAlias> {
        @Override
        public NetworkAddressAlias storage2Entity(final Convert2Entity converter) {
            final NetworkAddressAlias networkAddressAlias = new NetworkAddressAlias();
            networkAddressAlias.setAddress((String) converter.get(ADDRESS));
            networkAddressAlias.setRepresentServiceId((String) converter.get(REPRESENT_SERVICE_ID));
            networkAddressAlias.setRepresentServiceInstanceId((String) converter.get(REPRESENT_SERVICE_INSTANCE_ID));
            networkAddressAlias.setLastUpdateTimeBucket(((Number) converter.get(LAST_UPDATE_TIME_BUCKET)).longValue());
            networkAddressAlias.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            return networkAddressAlias;
        }

        @Override
        public void entity2Storage(final NetworkAddressAlias storageData, final Convert2Storage converter) {
            converter.accept(ADDRESS, storageData.getAddress());
            converter.accept(REPRESENT_SERVICE_ID, storageData.getRepresentServiceId());
            converter.accept(REPRESENT_SERVICE_INSTANCE_ID, storageData.getRepresentServiceInstanceId());
            converter.accept(LAST_UPDATE_TIME_BUCKET, storageData.getLastUpdateTimeBucket());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
        }
    }
}
