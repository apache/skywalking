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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.cache;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;

@Slf4j
public class NetworkAddressAliasEsDAO extends EsDAO implements INetworkAddressAliasDAO {
    protected final int resultWindowMaxSize;

    public NetworkAddressAliasEsDAO(ElasticSearchClient client, int resultWindowMaxSize) {
        super(client);
        this.resultWindowMaxSize = resultWindowMaxSize;
    }

    @Override
    public List<NetworkAddressAlias> loadLastUpdate(long timeBucketInMinute) {
        List<NetworkAddressAlias> networkAddressAliases = new ArrayList<>();

        try {
            final Search search =
                Search.builder().query(
                          Query.range(NetworkAddressAlias.LAST_UPDATE_TIME_BUCKET)
                               .gte(timeBucketInMinute))
                      .size(resultWindowMaxSize)
                      .build();

            final SearchResponse results =
                getClient().search(NetworkAddressAlias.INDEX_NAME, search);

            final NetworkAddressAlias.Builder builder = new NetworkAddressAlias.Builder();
            for (SearchHit searchHit : results.getHits()) {
                networkAddressAliases.add(builder.storage2Entity(searchHit.getSource()));
            }
        } catch (Throwable t) {
            log.error(t.getMessage(), t);
        }

        return networkAddressAliases;
    }
}
