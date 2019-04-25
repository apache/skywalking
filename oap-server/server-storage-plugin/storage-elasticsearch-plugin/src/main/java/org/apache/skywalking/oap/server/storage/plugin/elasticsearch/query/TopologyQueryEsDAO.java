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
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.analysis.manual.endpointrelation.EndpointRelationServerSideIndicator;
import org.apache.skywalking.oap.server.core.analysis.manual.servicerelation.ServiceRelationClientSideIndicator;
import org.apache.skywalking.oap.server.core.analysis.manual.servicerelation.ServiceRelationServerSideIndicator;
import org.apache.skywalking.oap.server.core.query.entity.Call;
import org.apache.skywalking.oap.server.core.query.entity.Step;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.source.ServiceRelation;
import org.apache.skywalking.oap.server.core.storage.DownSamplingModelNameBuilder;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;

/**
 * @author peng-yongsheng
 */
public class TopologyQueryEsDAO extends EsDAO implements ITopologyQueryDAO {

    public TopologyQueryEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public List<Call> loadSpecifiedServerSideServiceRelations(Step step, long startTB, long endTB,
        List<Integer> serviceIds) throws IOException {
        if (CollectionUtils.isEmpty(serviceIds)) {
            throw new UnexpectedException("Service id is empty");
        }

        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.size(0);
        setQueryCondition(sourceBuilder, startTB, endTB, serviceIds);

        String indexName = DownSamplingModelNameBuilder.build(step, ServiceRelationServerSideIndicator.INDEX_NAME);
        return load(sourceBuilder, indexName, DetectPoint.SERVER);
    }

    @Override
    public List<Call> loadSpecifiedClientSideServiceRelations(Step step, long startTB, long endTB,
        List<Integer> serviceIds) throws IOException {
        if (CollectionUtils.isEmpty(serviceIds)) {
            throw new UnexpectedException("Service id is empty");
        }

        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.size(0);
        setQueryCondition(sourceBuilder, startTB, endTB, serviceIds);

        String indexName = DownSamplingModelNameBuilder.build(step, ServiceRelationClientSideIndicator.INDEX_NAME);
        return load(sourceBuilder, indexName, DetectPoint.CLIENT);
    }

    private void setQueryCondition(SearchSourceBuilder sourceBuilder, long startTB, long endTB,
        List<Integer> serviceIds) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(ServiceRelationServerSideIndicator.TIME_BUCKET).gte(startTB).lte(endTB));

        BoolQueryBuilder serviceIdBoolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(serviceIdBoolQuery);

        if (serviceIds.size() == 1) {
            serviceIdBoolQuery.should().add(QueryBuilders.termQuery(ServiceRelationServerSideIndicator.SOURCE_SERVICE_ID, serviceIds.get(0)));
            serviceIdBoolQuery.should().add(QueryBuilders.termQuery(ServiceRelationServerSideIndicator.DEST_SERVICE_ID, serviceIds.get(0)));
        } else {
            serviceIdBoolQuery.should().add(QueryBuilders.termsQuery(ServiceRelationServerSideIndicator.SOURCE_SERVICE_ID, serviceIds));
            serviceIdBoolQuery.should().add(QueryBuilders.termsQuery(ServiceRelationServerSideIndicator.DEST_SERVICE_ID, serviceIds));
        }
        sourceBuilder.query(boolQuery);
    }

    @Override public List<Call> loadServerSideServiceRelations(Step step, long startTB, long endTB) throws IOException {
        String indexName = DownSamplingModelNameBuilder.build(step, ServiceRelationServerSideIndicator.INDEX_NAME);
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.query(QueryBuilders.rangeQuery(ServiceRelationServerSideIndicator.TIME_BUCKET).gte(startTB).lte(endTB));
        sourceBuilder.size(0);

        return load(sourceBuilder, indexName, DetectPoint.SERVER);
    }

    @Override public List<Call> loadClientSideServiceRelations(Step step, long startTB, long endTB) throws IOException {
        String indexName = DownSamplingModelNameBuilder.build(step, ServiceRelationClientSideIndicator.INDEX_NAME);
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.query(QueryBuilders.rangeQuery(ServiceRelationServerSideIndicator.TIME_BUCKET).gte(startTB).lte(endTB));
        sourceBuilder.size(0);

        return load(sourceBuilder, indexName, DetectPoint.CLIENT);
    }

    @Override
    public List<Call> loadSpecifiedDestOfServerSideEndpointRelations(Step step, long startTB, long endTB,
        int destEndpointId) throws IOException {
        String indexName = DownSamplingModelNameBuilder.build(step, EndpointRelationServerSideIndicator.INDEX_NAME);

        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.size(0);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(EndpointRelationServerSideIndicator.TIME_BUCKET).gte(startTB).lte(endTB));

        BoolQueryBuilder serviceIdBoolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(serviceIdBoolQuery);
        serviceIdBoolQuery.should().add(QueryBuilders.termQuery(EndpointRelationServerSideIndicator.SOURCE_ENDPOINT_ID, destEndpointId));
        serviceIdBoolQuery.should().add(QueryBuilders.termQuery(EndpointRelationServerSideIndicator.DEST_ENDPOINT_ID, destEndpointId));

        sourceBuilder.query(boolQuery);

        return load(sourceBuilder, indexName, DetectPoint.SERVER);
    }

    private List<Call> load(SearchSourceBuilder sourceBuilder, String indexName,
        DetectPoint detectPoint) throws IOException {
        sourceBuilder.aggregation(AggregationBuilders.terms(Indicator.ENTITY_ID).field(Indicator.ENTITY_ID).size(1000));

        SearchResponse response = getClient().search(indexName, sourceBuilder);

        List<Call> calls = new ArrayList<>();
        Terms entityTerms = response.getAggregations().get(Indicator.ENTITY_ID);
        for (Terms.Bucket entityBucket : entityTerms.getBuckets()) {
            String entityId = entityBucket.getKeyAsString();

            Integer[] entityIds = ServiceRelation.splitEntityId(entityId);
            Call call = new Call();
            call.setId(entityId);
            call.setSource(entityIds[0]);
            call.setTarget(entityIds[1]);
            call.setComponentId(entityIds[2]);
            call.setDetectPoint(detectPoint);
            calls.add(call);
        }
        return calls;
    }
}
