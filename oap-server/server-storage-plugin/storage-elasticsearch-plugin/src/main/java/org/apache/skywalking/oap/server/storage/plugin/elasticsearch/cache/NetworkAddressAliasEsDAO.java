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

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchScroller;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchConfig;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.ElasticSearchConverter;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;

import java.util.Collections;
import java.util.List;

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
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(NetworkAddressAlias.INDEX_NAME);
        try {
            final int batchSize = Math.min(resultWindowMaxSize, scrollingBatchSize);
            final BoolQueryBuilder query = Query.bool();
            if (IndexController.LogicIndicesRegister.isMergedTable(NetworkAddressAlias.INDEX_NAME)) {
                query.must(Query.term(IndexController.LogicIndicesRegister.METRIC_TABLE_NAME, NetworkAddressAlias.INDEX_NAME));
            }
            query.must(Query.range(NetworkAddressAlias.LAST_UPDATE_TIME_BUCKET)
                             .gte(timeBucketInMinute));

            final var search = Search.builder().query(query).size(batchSize).build();
            final var builder = new NetworkAddressAlias.Builder();

            final var scroller = ElasticSearchScroller
                .<NetworkAddressAlias>builder()
                .client(getClient())
                .search(search)
                .index(index)
                .queryMaxSize(resultWindowMaxSize)
                .resultConverter(searchHit -> builder.storage2Entity(
                    new ElasticSearchConverter.ToEntity(NetworkAddressAlias.INDEX_NAME, searchHit.getSource())))
                .build();
            return scroller.scroll();
        } catch (Throwable t) {
            log.error(t.getMessage(), t);
            return Collections.emptyList();
        }
    }
}
