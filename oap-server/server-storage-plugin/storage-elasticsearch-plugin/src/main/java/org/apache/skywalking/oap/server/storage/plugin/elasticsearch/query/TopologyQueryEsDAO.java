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
import org.apache.skywalking.oap.server.core.analysis.manual.relation.endpoint.EndpointRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.type.Call;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
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

public class TopologyQueryEsDAO extends EsDAO implements ITopologyQueryDAO {

    public TopologyQueryEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(long startTB,
                                                                          long endTB,
                                                                          List<String> serviceIds) throws IOException {
        if (CollectionUtils.isEmpty(serviceIds)) {
            throw new UnexpectedException("Service id is empty");
        }

        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.size(0);
        setQueryCondition(sourceBuilder, startTB, endTB, serviceIds);

        return buildServiceRelation(sourceBuilder, ServiceRelationServerSideMetrics.INDEX_NAME, DetectPoint.SERVER);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(long startTB,
                                                                         long endTB,
                                                                         List<String> serviceIds) throws IOException {
        if (CollectionUtils.isEmpty(serviceIds)) {
            throw new UnexpectedException("Service id is empty");
        }

        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.size(0);
        setQueryCondition(sourceBuilder, startTB, endTB, serviceIds);

        return buildServiceRelation(sourceBuilder, ServiceRelationClientSideMetrics.INDEX_NAME, DetectPoint.CLIENT);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(long startTB,
                                                                          long endTB) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.query(QueryBuilders.rangeQuery(ServiceRelationServerSideMetrics.TIME_BUCKET)
                                         .gte(startTB)
                                         .lte(endTB));
        sourceBuilder.size(0);

        return buildServiceRelation(sourceBuilder, ServiceRelationServerSideMetrics.INDEX_NAME, DetectPoint.SERVER);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(long startTB,
                                                                         long endTB) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.query(QueryBuilders.rangeQuery(ServiceRelationServerSideMetrics.TIME_BUCKET)
                                         .gte(startTB)
                                         .lte(endTB));
        sourceBuilder.size(0);

        return buildServiceRelation(sourceBuilder, ServiceRelationClientSideMetrics.INDEX_NAME, DetectPoint.CLIENT);
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtServerSide(String clientServiceId,
                                                                          String serverServiceId,
                                                                          long startTB,
                                                                          long endTB) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.size(0);
        setInstanceQueryCondition(sourceBuilder, startTB, endTB, clientServiceId, serverServiceId);

        return buildInstanceRelation(
            sourceBuilder, ServiceInstanceRelationServerSideMetrics.INDEX_NAME, DetectPoint.SERVER);
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtClientSide(String clientServiceId,
                                                                          String serverServiceId,
                                                                          long startTB,
                                                                          long endTB) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.size(0);
        setInstanceQueryCondition(sourceBuilder, startTB, endTB, clientServiceId, serverServiceId);

        return buildInstanceRelation(
            sourceBuilder, ServiceInstanceRelationClientSideMetrics.INDEX_NAME, DetectPoint.CLIENT);
    }

    private void setInstanceQueryCondition(SearchSourceBuilder sourceBuilder, long startTB, long endTB,
                                           String clientServiceId, String serverServiceId) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must()
                 .add(QueryBuilders.rangeQuery(EndpointRelationServerSideMetrics.TIME_BUCKET).gte(startTB).lte(endTB));

        BoolQueryBuilder serviceIdBoolQuery = new BoolQueryBuilder();
        boolQuery.must(serviceIdBoolQuery);

        BoolQueryBuilder serverRelationBoolQuery = new BoolQueryBuilder();
        serviceIdBoolQuery.should(serverRelationBoolQuery);

        serverRelationBoolQuery.must(
            QueryBuilders.termQuery(ServiceInstanceRelationServerSideMetrics.SOURCE_SERVICE_ID, clientServiceId));
        serverRelationBoolQuery.must(
            QueryBuilders.termQuery(ServiceInstanceRelationServerSideMetrics.DEST_SERVICE_ID, serverServiceId));

        BoolQueryBuilder clientRelationBoolQuery = new BoolQueryBuilder();
        serviceIdBoolQuery.should(clientRelationBoolQuery);

        clientRelationBoolQuery.must(
            QueryBuilders.termQuery(ServiceInstanceRelationServerSideMetrics.DEST_SERVICE_ID, clientServiceId));
        clientRelationBoolQuery.must(
            QueryBuilders.termQuery(ServiceInstanceRelationServerSideMetrics.SOURCE_SERVICE_ID, serverServiceId));

        sourceBuilder.query(boolQuery);
    }

    @Override
    public List<Call.CallDetail> loadEndpointRelation(long startTB,
                                                      long endTB,
                                                      String destEndpointId) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.size(0);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must()
                 .add(QueryBuilders.rangeQuery(EndpointRelationServerSideMetrics.TIME_BUCKET).gte(startTB).lte(endTB));

        BoolQueryBuilder serviceIdBoolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(serviceIdBoolQuery);
        serviceIdBoolQuery.should()
                          .add(QueryBuilders.termQuery(
                              EndpointRelationServerSideMetrics.SOURCE_ENDPOINT, destEndpointId
                          ));
        serviceIdBoolQuery.should()
                          .add(QueryBuilders.termQuery(
                              EndpointRelationServerSideMetrics.DEST_ENDPOINT, destEndpointId
                          ));

        sourceBuilder.query(boolQuery);

        return loadEndpoint(sourceBuilder, EndpointRelationServerSideMetrics.INDEX_NAME, DetectPoint.SERVER);
    }

    private List<Call.CallDetail> buildServiceRelation(SearchSourceBuilder sourceBuilder, String indexName,
                                                       DetectPoint detectPoint) throws IOException {
        sourceBuilder.aggregation(
            AggregationBuilders
                .terms(Metrics.ENTITY_ID).field(Metrics.ENTITY_ID)
                .subAggregation(
                    AggregationBuilders.terms(ServiceRelationServerSideMetrics.COMPONENT_ID)
                                       .field(ServiceRelationServerSideMetrics.COMPONENT_ID))
                .size(1000));

        SearchResponse response = getClient().search(indexName, sourceBuilder);

        List<Call.CallDetail> calls = new ArrayList<>();
        Terms entityTerms = response.getAggregations().get(Metrics.ENTITY_ID);
        for (Terms.Bucket entityBucket : entityTerms.getBuckets()) {
            String entityId = entityBucket.getKeyAsString();
            Terms componentTerms = entityBucket.getAggregations().get(ServiceRelationServerSideMetrics.COMPONENT_ID);
            final int componentId = componentTerms.getBuckets().get(0).getKeyAsNumber().intValue();

            Call.CallDetail call = new Call.CallDetail();
            call.buildFromServiceRelation(entityId, componentId, detectPoint);
            calls.add(call);
        }
        return calls;
    }

    private List<Call.CallDetail> buildInstanceRelation(SearchSourceBuilder sourceBuilder, String indexName,
                                                        DetectPoint detectPoint) throws IOException {
        sourceBuilder.aggregation(
            AggregationBuilders
                .terms(Metrics.ENTITY_ID).field(Metrics.ENTITY_ID)
                .subAggregation(
                    AggregationBuilders.terms(ServiceInstanceRelationServerSideMetrics.COMPONENT_ID)
                                       .field(ServiceInstanceRelationServerSideMetrics.COMPONENT_ID))
                .size(1000));

        SearchResponse response = getClient().search(indexName, sourceBuilder);

        List<Call.CallDetail> calls = new ArrayList<>();
        Terms entityTerms = response.getAggregations().get(Metrics.ENTITY_ID);
        for (Terms.Bucket entityBucket : entityTerms.getBuckets()) {
            String entityId = entityBucket.getKeyAsString();
            Terms componentTerms = entityBucket.getAggregations()
                                               .get(ServiceInstanceRelationServerSideMetrics.COMPONENT_ID);
            final int componentId = componentTerms.getBuckets().get(0).getKeyAsNumber().intValue();

            Call.CallDetail call = new Call.CallDetail();
            call.buildFromInstanceRelation(entityId, componentId, detectPoint);
            calls.add(call);
        }
        return calls;
    }

    private List<Call.CallDetail> loadEndpoint(SearchSourceBuilder sourceBuilder, String indexName,
                                               DetectPoint detectPoint) throws IOException {
        sourceBuilder.aggregation(AggregationBuilders.terms(Metrics.ENTITY_ID).field(Metrics.ENTITY_ID).size(1000));

        SearchResponse response = getClient().search(indexName, sourceBuilder);

        List<Call.CallDetail> calls = new ArrayList<>();
        Terms entityTerms = response.getAggregations().get(Metrics.ENTITY_ID);
        for (Terms.Bucket entityBucket : entityTerms.getBuckets()) {
            String entityId = entityBucket.getKeyAsString();

            Call.CallDetail call = new Call.CallDetail();
            call.buildFromEndpointRelation(entityId, detectPoint);
            calls.add(call);
        }
        return calls;
    }

    private void setQueryCondition(SearchSourceBuilder sourceBuilder, long startTB, long endTB,
                                   List<String> serviceIds) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must()
                 .add(QueryBuilders.rangeQuery(ServiceRelationServerSideMetrics.TIME_BUCKET).gte(startTB).lte(endTB));

        BoolQueryBuilder serviceIdBoolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(serviceIdBoolQuery);

        if (serviceIds.size() == 1) {
            serviceIdBoolQuery.should()
                              .add(
                                  QueryBuilders.termQuery(ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID, serviceIds
                                      .get(0)));
            serviceIdBoolQuery.should()
                              .add(QueryBuilders.termQuery(
                                  ServiceRelationServerSideMetrics.DEST_SERVICE_ID,
                                  serviceIds.get(0)
                              ));
        } else {
            serviceIdBoolQuery.should()
                              .add(QueryBuilders.termsQuery(
                                  ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID,
                                  serviceIds
                              ));
            serviceIdBoolQuery.should()
                              .add(QueryBuilders.termsQuery(
                                  ServiceRelationServerSideMetrics.DEST_SERVICE_ID,
                                  serviceIds
                              ));
        }
        sourceBuilder.query(boolQuery);
    }
}
