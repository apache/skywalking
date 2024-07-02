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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.measure;

import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.v1.client.MeasureQuery;
import org.apache.skywalking.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBConverter.StorageToMeasure;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.AbstractBanyanDBDAO;

@Slf4j
public class BanyanDBNetworkAddressAliasDAO extends AbstractBanyanDBDAO implements INetworkAddressAliasDAO {
    private final NetworkAddressAlias.Builder builder = new NetworkAddressAlias.Builder();

    private MetadataRegistry.Schema schema;

    private static final Set<String> TAGS = ImmutableSet.of(NetworkAddressAlias.ADDRESS,
            NetworkAddressAlias.LAST_UPDATE_TIME_BUCKET,
            NetworkAddressAlias.REPRESENT_SERVICE_ID, NetworkAddressAlias.REPRESENT_SERVICE_INSTANCE_ID);

    public BanyanDBNetworkAddressAliasDAO(final BanyanDBStorageClient client) {
        super(client);
    }

    private MetadataRegistry.Schema getSchema() {
        if (schema == null) {
            schema = MetadataRegistry.INSTANCE.findMetadata(NetworkAddressAlias.INDEX_NAME, DownSampling.Minute);
        }
        return schema;
    }

    @Override
    public List<NetworkAddressAlias> loadLastUpdate(long timeBucket) {
        try {
            MeasureQueryResponse resp = query(
                    getSchema(),
                    TAGS,
                    Collections.emptySet(),
                    new QueryBuilder<MeasureQuery>() {
                        @Override
                        protected void apply(final MeasureQuery query) {
                            query.and(gte(NetworkAddressAlias.LAST_UPDATE_TIME_BUCKET, timeBucket));
                        }
                    }
            );
            /**
             * Currently, only used by {@link org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBNetworkAddressAliasDAO}.
             * The default DownSampling strategy, i.e. {@link DownSampling#Minute} is assumed in this case.
             */
            return resp.getDataPoints()
                    .stream()
                    .map(
                            point -> builder.storage2Entity(new StorageToMeasure(getSchema(), point))
                    )
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return Collections.emptyList();
    }
}
