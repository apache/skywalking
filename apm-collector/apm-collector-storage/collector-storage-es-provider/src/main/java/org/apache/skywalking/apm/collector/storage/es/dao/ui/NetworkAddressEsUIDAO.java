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

package org.apache.skywalking.apm.collector.storage.es.dao.ui;

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.storage.dao.ui.INetworkAddressUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddressTable;
import org.apache.skywalking.apm.collector.storage.ui.overview.ConjecturalApp;
import org.apache.skywalking.apm.network.proto.SpanLayer;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;

/**
 * @author peng-yongsheng
 */
public class NetworkAddressEsUIDAO extends EsDAO implements INetworkAddressUIDAO {

    public NetworkAddressEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public int getNumOfSpanLayer(int srcSpanLayer) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(NetworkAddressTable.TABLE);
        searchRequestBuilder.setTypes(NetworkAddressTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setQuery(QueryBuilders.termQuery(NetworkAddressTable.SRC_SPAN_LAYER.getName(), srcSpanLayer));
        searchRequestBuilder.setSize(0);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        return (int)searchResponse.getHits().getTotalHits();
    }

    @Override public List<ConjecturalApp> getConjecturalApps() {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(NetworkAddressTable.TABLE);
        searchRequestBuilder.setTypes(NetworkAddressTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        int[] spanLayers = new int[] {SpanLayer.Database_VALUE, SpanLayer.Cache_VALUE, SpanLayer.MQ_VALUE};
        searchRequestBuilder.setQuery(QueryBuilders.termsQuery(NetworkAddressTable.SRC_SPAN_LAYER.getName(), spanLayers));
        searchRequestBuilder.setSize(0);

        TermsAggregationBuilder aggregationBuilder = AggregationBuilders.terms(NetworkAddressTable.SERVER_TYPE.getName()).field(NetworkAddressTable.SERVER_TYPE.getName()).size(100);

        searchRequestBuilder.addAggregation(aggregationBuilder);
        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        List<ConjecturalApp> conjecturalApps = new LinkedList<>();
        Terms serverTypeTerms = searchResponse.getAggregations().get(NetworkAddressTable.SERVER_TYPE.getName());
        serverTypeTerms.getBuckets().forEach(serverTypeTerm -> {
            int serverType = serverTypeTerm.getKeyAsNumber().intValue();

            ConjecturalApp conjecturalApp = new ConjecturalApp();
            conjecturalApp.setId(serverType);
            conjecturalApp.setNum((int)serverTypeTerm.getDocCount());
            conjecturalApps.add(conjecturalApp);
        });
        return conjecturalApps;
    }
}
