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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.query;

import org.apache.skywalking.oap.server.core.register.EndpointInventory;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.MetadataQueryEsDAO;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;

/**
 * @author peng-yongsheng
 */
public class MetadataQueryEs7DAO extends MetadataQueryEsDAO {

    public MetadataQueryEs7DAO(final ElasticSearchClient client, final int queryMaxSize) {
        super(client, queryMaxSize);
    }

    @Override
    public int numOfService(long startTimestamp, long endTimestamp) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must().add(timeRangeQueryBuild(startTimestamp, endTimestamp));

        boolQueryBuilder.must().add(QueryBuilders.termQuery(ServiceInventory.IS_ADDRESS, BooleanUtils.FALSE));

        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(0);

        SearchResponse response = getClient().search(ServiceInventory.INDEX_NAME, sourceBuilder);
        return (int) response.getHits().getTotalHits().value;
    }

    @Override
    public int numOfEndpoint() throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        boolQueryBuilder.must().add(QueryBuilders.termQuery(EndpointInventory.DETECT_POINT, DetectPoint.SERVER.ordinal()));

        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(0);

        SearchResponse response = getClient().search(EndpointInventory.INDEX_NAME, sourceBuilder);
        return (int) response.getHits().getTotalHits().value;
    }

    @Override
    public int numOfConjectural(int nodeTypeValue) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        sourceBuilder.query(QueryBuilders.termQuery(ServiceInventory.NODE_TYPE, nodeTypeValue));
        sourceBuilder.size(0);

        SearchResponse response = getClient().search(ServiceInventory.INDEX_NAME, sourceBuilder);

        return (int) response.getHits().getTotalHits().value;
    }

}
