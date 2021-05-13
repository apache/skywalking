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
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

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
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.rangeQuery(NetworkAddressAlias.LAST_UPDATE_TIME_BUCKET)
                                                   .gte(timeBucketInMinute));
            searchSourceBuilder.size(resultWindowMaxSize);

            SearchResponse response = getClient().search(NetworkAddressAlias.INDEX_NAME, searchSourceBuilder);

            final NetworkAddressAlias.Builder builder = new NetworkAddressAlias.Builder();
            for (SearchHit searchHit : response.getHits().getHits()) {
                networkAddressAliases.add(builder.storage2Entity(searchHit.getSourceAsMap()));
            }
        } catch (Throwable t) {
            log.error(t.getMessage(), t);
        }

        return networkAddressAliases;
    }
}
