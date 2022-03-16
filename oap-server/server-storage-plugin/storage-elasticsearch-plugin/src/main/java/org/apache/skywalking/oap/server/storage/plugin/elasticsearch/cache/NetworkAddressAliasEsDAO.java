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
import org.apache.skywalking.library.elasticsearch.requests.search.SearchParams;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchConfig;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;

@Slf4j
public class NetworkAddressAliasEsDAO extends EsDAO implements INetworkAddressAliasDAO {
    protected final int resultWindowMaxSize;
    protected final int scrollingBatchSize;

    public NetworkAddressAliasEsDAO(ElasticSearchClient client,
                                    StorageModuleElasticsearchConfig config) {
        super(client);
        this.resultWindowMaxSize = config.getResultWindowMaxSize();
        this.scrollingBatchSize = config.getScrollingBatchSize();
    }

    @Override
    public List<NetworkAddressAlias> loadLastUpdate(long timeBucketInMinute) {
        List<NetworkAddressAlias> networkAddressAliases = new ArrayList<>();

        try {
            final int batchSize = Math.min(resultWindowMaxSize, scrollingBatchSize);
            final Search search =
                Search.builder().query(
                          Query.range(NetworkAddressAlias.LAST_UPDATE_TIME_BUCKET)
                               .gte(timeBucketInMinute))
                      .size(batchSize)
                      .build();
            final SearchParams params = new SearchParams().scroll(SCROLL_CONTEXT_RETENTION);
            final NetworkAddressAlias.Builder builder = new NetworkAddressAlias.Builder();

            SearchResponse results =
                getClient().search(NetworkAddressAlias.INDEX_NAME, search, params);
            while (results.getHits().getTotal() > 0) {
                for (SearchHit searchHit : results.getHits()) {
                    networkAddressAliases.add(
                        builder.storage2Entity(new HashMapConverter.ToEntity(searchHit.getSource())));
                }
                if (results.getHits().getTotal() < batchSize) {
                    break;
                }
                if (networkAddressAliases.size() >= resultWindowMaxSize) {
                    break;
                }
                results = getClient().scroll(SCROLL_CONTEXT_RETENTION, results.getScrollId());
            }
        } catch (Throwable t) {
            log.error(t.getMessage(), t);
        }

        return networkAddressAliases;
    }
}
