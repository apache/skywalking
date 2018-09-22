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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query;

import java.io.IOException;
import java.util.*;
import org.apache.skywalking.oap.server.core.query.entity.Service;
import org.apache.skywalking.oap.server.core.register.*;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

/**
 * @author peng-yongsheng
 */
public class MetadataQueryEsDAO extends EsDAO implements IMetadataQueryDAO {

    public MetadataQueryEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public int numOfService(long startTimestamp, long endTimestamp) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        BoolQueryBuilder boolQuery1 = QueryBuilders.boolQuery();
        boolQuery1.must().add(QueryBuilders.rangeQuery(ServiceInventory.HEARTBEAT_TIME).gte(endTimestamp));
        boolQuery1.must().add(QueryBuilders.rangeQuery(ServiceInventory.REGISTER_TIME).lte(endTimestamp));

        BoolQueryBuilder boolQuery2 = QueryBuilders.boolQuery();
        boolQuery2.must().add(QueryBuilders.rangeQuery(ServiceInventory.REGISTER_TIME).lte(endTimestamp));
        boolQuery2.must().add(QueryBuilders.rangeQuery(ServiceInventory.HEARTBEAT_TIME).gte(startTimestamp));

        BoolQueryBuilder timeBoolQuery = QueryBuilders.boolQuery();
        timeBoolQuery.should().add(boolQuery1);
        timeBoolQuery.should().add(boolQuery2);

        boolQueryBuilder.must().add(timeBoolQuery);

        boolQueryBuilder.must().add(QueryBuilders.termQuery(ServiceInventory.IS_ADDRESS, BooleanUtils.FALSE));

        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(0);

        SearchResponse response = getClient().search(ServiceInventory.MODEL_NAME, sourceBuilder);
        return (int)response.getHits().getTotalHits();
    }

    @Override public int numOfEndpoint(long startTimestamp, long endTimestamp) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        BoolQueryBuilder boolQuery1 = QueryBuilders.boolQuery();
        boolQuery1.must().add(QueryBuilders.rangeQuery(EndpointInventory.HEARTBEAT_TIME).gte(endTimestamp));
        boolQuery1.must().add(QueryBuilders.rangeQuery(EndpointInventory.REGISTER_TIME).lte(endTimestamp));

        BoolQueryBuilder boolQuery2 = QueryBuilders.boolQuery();
        boolQuery2.must().add(QueryBuilders.rangeQuery(EndpointInventory.REGISTER_TIME).lte(endTimestamp));
        boolQuery2.must().add(QueryBuilders.rangeQuery(EndpointInventory.HEARTBEAT_TIME).gte(startTimestamp));

        BoolQueryBuilder timeBoolQuery = QueryBuilders.boolQuery();
        timeBoolQuery.should().add(boolQuery1);
        timeBoolQuery.should().add(boolQuery2);

        boolQueryBuilder.must().add(timeBoolQuery);

        boolQueryBuilder.must().add(QueryBuilders.termQuery(EndpointInventory.DETECT_POINT, DetectPoint.SERVER.ordinal()));

        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(0);

        SearchResponse response = getClient().search(EndpointInventory.MODEL_NAME, sourceBuilder);
        return (int)response.getHits().getTotalHits();
    }

    @Override public int numOfConjectural(long startTimestamp, long endTimestamp, int srcLayer) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        BoolQueryBuilder boolQuery1 = QueryBuilders.boolQuery();
        boolQuery1.must().add(QueryBuilders.rangeQuery(NetworkAddressInventory.HEARTBEAT_TIME).gte(endTimestamp));
        boolQuery1.must().add(QueryBuilders.rangeQuery(NetworkAddressInventory.REGISTER_TIME).lte(endTimestamp));

        BoolQueryBuilder boolQuery2 = QueryBuilders.boolQuery();
        boolQuery2.must().add(QueryBuilders.rangeQuery(NetworkAddressInventory.REGISTER_TIME).lte(endTimestamp));
        boolQuery2.must().add(QueryBuilders.rangeQuery(NetworkAddressInventory.HEARTBEAT_TIME).gte(startTimestamp));

        BoolQueryBuilder timeBoolQuery = QueryBuilders.boolQuery();
        timeBoolQuery.should().add(boolQuery1);
        timeBoolQuery.should().add(boolQuery2);

        boolQueryBuilder.must().add(timeBoolQuery);

        boolQueryBuilder.must().add(QueryBuilders.termQuery(NetworkAddressInventory.SRC_LAYER, srcLayer));

        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(0);

        SearchResponse response = getClient().search(NetworkAddressInventory.MODEL_NAME, sourceBuilder);

        return (int)response.getHits().getTotalHits();
    }

    @Override
    public List<Service> getAllServices(long startTimestamp, long endTimestamp) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        BoolQueryBuilder boolQuery1 = QueryBuilders.boolQuery();
        boolQuery1.must().add(QueryBuilders.rangeQuery(ServiceInventory.HEARTBEAT_TIME).gte(endTimestamp));
        boolQuery1.must().add(QueryBuilders.rangeQuery(ServiceInventory.REGISTER_TIME).lte(endTimestamp));

        BoolQueryBuilder boolQuery2 = QueryBuilders.boolQuery();
        boolQuery2.must().add(QueryBuilders.rangeQuery(ServiceInventory.REGISTER_TIME).lte(endTimestamp));
        boolQuery2.must().add(QueryBuilders.rangeQuery(ServiceInventory.HEARTBEAT_TIME).gte(startTimestamp));

        BoolQueryBuilder timeBoolQuery = QueryBuilders.boolQuery();
        timeBoolQuery.should().add(boolQuery1);
        timeBoolQuery.should().add(boolQuery2);

        boolQueryBuilder.must().add(timeBoolQuery);

        boolQueryBuilder.must().add(QueryBuilders.termQuery(ServiceInventory.IS_ADDRESS, BooleanUtils.FALSE));

        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(100);

        SearchResponse response = getClient().search(ServiceInventory.MODEL_NAME, sourceBuilder);

        List<Service> services = new ArrayList<>();
        for (SearchHit searchHit : response.getHits()) {
            Map<String, Object> sourceAsMap = searchHit.getSourceAsMap();

            Service service = new Service();
            service.setId(String.valueOf(sourceAsMap.get(ServiceInventory.SEQUENCE)));
            service.setName((String)sourceAsMap.get(ServiceInventory.NAME));
            services.add(service);
        }

        return services;
    }
}
