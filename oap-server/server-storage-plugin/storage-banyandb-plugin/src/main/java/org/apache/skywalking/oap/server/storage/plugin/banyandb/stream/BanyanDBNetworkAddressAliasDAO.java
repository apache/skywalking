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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import com.google.common.collect.ImmutableList;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link NetworkAddressAlias} is a stream
 */
public class BanyanDBNetworkAddressAliasDAO extends AbstractBanyanDBDAO implements INetworkAddressAliasDAO {
    public BanyanDBNetworkAddressAliasDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<NetworkAddressAlias> loadLastUpdate(long timeBucket) {
        StreamQueryResponse resp = query(NetworkAddressAlias.INDEX_NAME,
                ImmutableList.of(NetworkAddressAlias.LAST_UPDATE_TIME_BUCKET),
                new QueryBuilder() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.setDataProjections(ImmutableList.of(Metrics.TIME_BUCKET, "address", "represent_service_id", "represent_service_instance_id"));
                        query.appendCondition(gte(NetworkAddressAlias.LAST_UPDATE_TIME_BUCKET, timeBucket));
                    }
                });

        return resp.getElements().stream().map(new NetworkAddressAliasDeserializer()).collect(Collectors.toList());
    }

    public static class NetworkAddressAliasDeserializer implements RowEntityDeserializer<NetworkAddressAlias> {
        @Override
        public NetworkAddressAlias apply(RowEntity row) {
            NetworkAddressAlias model = new NetworkAddressAlias();
            final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
            // searchable - last_update_time_bucket
            model.setLastUpdateTimeBucket(((Number) searchable.get(0).getValue()).longValue());
            final List<TagAndValue<?>> data = row.getTagFamilies().get(1);
            // data 0 - time_bucket
            model.setTimeBucket(((Number) data.get(0).getValue()).longValue());
            // data 1 - address
            model.setAddress((String) data.get(1).getValue());
            // data 2 - represent_service_id
            model.setRepresentServiceId((String) data.get(2).getValue());
            // data 3 - represent_service_instance_id
            model.setRepresentServiceInstanceId((String) data.get(3).getValue());
            return model;
        }
    }
}
