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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.aggregation.Aggregation;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
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
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;

public class TopologyQueryEsDAO extends EsDAO implements ITopologyQueryDAO {

    public TopologyQueryEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(
        long startTB, long endTB, List<String> serviceIds) {
        if (CollectionUtils.isEmpty(serviceIds)) {
            throw new UnexpectedException("Service id is empty");
        }

        final SearchBuilder sourceBuilder = Search.builder().size(0);
        setQueryCondition(sourceBuilder, startTB, endTB, serviceIds);

        return buildServiceRelation(
            sourceBuilder, ServiceRelationServerSideMetrics.INDEX_NAME, DetectPoint.SERVER);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(long startTB,
                                                                         long endTB,
                                                                         List<String> serviceIds) {
        if (CollectionUtils.isEmpty(serviceIds)) {
            throw new UnexpectedException("Service id is empty");
        }

        final SearchBuilder sourceBuilder = Search.builder().size(0);
        setQueryCondition(sourceBuilder, startTB, endTB, serviceIds);

        return buildServiceRelation(
            sourceBuilder, ServiceRelationClientSideMetrics.INDEX_NAME, DetectPoint.CLIENT);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(long startTB,
                                                                          long endTB) {
        SearchBuilder sourceBuilder = Search.builder();
        sourceBuilder.query(Query.range(ServiceRelationServerSideMetrics.TIME_BUCKET)
                                 .gte(startTB)
                                 .lte(endTB))
                     .size(0);

        return buildServiceRelation(
            sourceBuilder, ServiceRelationServerSideMetrics.INDEX_NAME, DetectPoint.SERVER);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(long startTB,
                                                                         long endTB) {
        SearchBuilder sourceBuilder = Search.builder();
        sourceBuilder.query(Query.range(ServiceRelationServerSideMetrics.TIME_BUCKET)
                                 .gte(startTB)
                                 .lte(endTB))
                     .size(0);

        return buildServiceRelation(
            sourceBuilder, ServiceRelationClientSideMetrics.INDEX_NAME, DetectPoint.CLIENT);
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtServerSide(String clientServiceId,
                                                                          String serverServiceId,
                                                                          long startTB,
                                                                          long endTB) {
        final SearchBuilder search = Search.builder().size(0);
        setInstanceQueryCondition(search, startTB, endTB, clientServiceId, serverServiceId);

        return buildInstanceRelation(
            search, ServiceInstanceRelationServerSideMetrics.INDEX_NAME, DetectPoint.SERVER);
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtClientSide(String clientServiceId,
                                                                          String serverServiceId,
                                                                          long startTB,
                                                                          long endTB) {
        final SearchBuilder search = Search.builder().size(0);
        setInstanceQueryCondition(search, startTB, endTB, clientServiceId, serverServiceId);

        return buildInstanceRelation(
            search, ServiceInstanceRelationClientSideMetrics.INDEX_NAME, DetectPoint.CLIENT);
    }

    private void setInstanceQueryCondition(SearchBuilder search, long startTB, long endTB,
                                           String clientServiceId, String serverServiceId) {
        final BoolQueryBuilder serverRelationBoolQuery =
            Query.bool()
                 .must(
                     Query.term(
                         ServiceInstanceRelationServerSideMetrics.SOURCE_SERVICE_ID,
                         clientServiceId
                     ))
                 .must(
                     Query.term(
                         ServiceInstanceRelationServerSideMetrics.DEST_SERVICE_ID,
                         serverServiceId
                     ));

        final BoolQueryBuilder clientRelationBoolQuery =
            Query.bool()
                 .must(
                     Query.term(
                         ServiceInstanceRelationServerSideMetrics.DEST_SERVICE_ID,
                         clientServiceId
                     ))
                 .must(
                     Query.term(
                         ServiceInstanceRelationServerSideMetrics.SOURCE_SERVICE_ID,
                         serverServiceId
                     ));

        final BoolQueryBuilder serviceIdBoolQuery =
            Query.bool()
                 .should(serverRelationBoolQuery)
                 .should(clientRelationBoolQuery);

        final BoolQueryBuilder boolQuery =
            Query.bool()
                 .must(Query.range(EndpointRelationServerSideMetrics.TIME_BUCKET)
                            .gte(startTB)
                            .lte(endTB))
                 .must(serviceIdBoolQuery);

        search.query(boolQuery);
    }

    @Override
    public List<Call.CallDetail> loadEndpointRelation(long startTB, long endTB,
                                                      String destEndpointId) {
        SearchBuilder sourceBuilder = Search.builder();
        sourceBuilder.size(0);

        BoolQueryBuilder boolQuery = Query.bool();
        boolQuery.must(
            Query.range(EndpointRelationServerSideMetrics.TIME_BUCKET)
                 .gte(startTB).lte(endTB));

        BoolQueryBuilder serviceIdBoolQuery = Query.bool();
        boolQuery.must(serviceIdBoolQuery);
        serviceIdBoolQuery.should(
            Query.term(
                EndpointRelationServerSideMetrics.SOURCE_ENDPOINT, destEndpointId
            ));
        serviceIdBoolQuery.must(
            Query.term(
                EndpointRelationServerSideMetrics.DEST_ENDPOINT, destEndpointId
            ));

        sourceBuilder.query(boolQuery);

        return loadEndpoint(
            sourceBuilder, EndpointRelationServerSideMetrics.INDEX_NAME, DetectPoint.SERVER);
    }

    private List<Call.CallDetail> buildServiceRelation(SearchBuilder sourceBuilder,
                                                       String indexName,
                                                       DetectPoint detectPoint) {
        sourceBuilder.aggregation(
            Aggregation
                .terms(Metrics.ENTITY_ID).field(Metrics.ENTITY_ID)
                .subAggregation(
                    Aggregation.terms(ServiceRelationServerSideMetrics.COMPONENT_ID)
                               .field(ServiceRelationServerSideMetrics.COMPONENT_ID))
                .size(1000));

        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(indexName);
        final SearchResponse response = getClient().search(index, sourceBuilder.build());

        final List<Call.CallDetail> calls = new ArrayList<>();
        final Map<String, Object> entityTerms =
            (Map<String, Object>) response.getAggregations().get(Metrics.ENTITY_ID);
        final List<Map<String, Object>> buckets =
            (List<Map<String, Object>>) entityTerms.get("buckets");
        for (final Map<String, Object> entityBucket : buckets) {
            String entityId = (String) entityBucket.get("key");
            final Map<String, Object> componentTerms =
                (Map<String, Object>) entityBucket.get(
                    ServiceRelationServerSideMetrics.COMPONENT_ID);
            final List<Map<String, Object>> subAgg =
                (List<Map<String, Object>>) componentTerms.get("buckets");
            final int componentId = ((Number) subAgg.iterator().next().get("key")).intValue();

            Call.CallDetail call = new Call.CallDetail();
            call.buildFromServiceRelation(entityId, componentId, detectPoint);
            calls.add(call);
        }
        return calls;
    }

    private List<Call.CallDetail> buildInstanceRelation(SearchBuilder sourceBuilder,
                                                        String indexName,
                                                        DetectPoint detectPoint) {
        sourceBuilder.aggregation(
            Aggregation
                .terms(Metrics.ENTITY_ID).field(Metrics.ENTITY_ID)
                .subAggregation(
                    Aggregation.terms(ServiceInstanceRelationServerSideMetrics.COMPONENT_ID)
                               .field(
                                   ServiceInstanceRelationServerSideMetrics.COMPONENT_ID))
                .size(1000));

        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(indexName);
        SearchResponse response = getClient().search(index, sourceBuilder.build());

        List<Call.CallDetail> calls = new ArrayList<>();
        final Map<String, Object> entityTerms =
            (Map<String, Object>) response.getAggregations().get(Metrics.ENTITY_ID);
        final List<Map<String, Object>> buckets =
            (List<Map<String, Object>>) entityTerms.get("buckets");
        for (Map<String, Object> entityBucket : buckets) {
            final String entityId = (String) entityBucket.get("key");
            final Map<String, Object> componentTerms = (Map<String, Object>) entityBucket.get(
                ServiceInstanceRelationServerSideMetrics.COMPONENT_ID);
            final List<Map<String, Object>> subAgg =
                (List<Map<String, Object>>) componentTerms.get("buckets");
            final int componentId = ((Number) subAgg.iterator().next().get("key")).intValue();

            Call.CallDetail call = new Call.CallDetail();
            call.buildFromInstanceRelation(entityId, componentId, detectPoint);
            calls.add(call);
        }
        return calls;
    }

    private List<Call.CallDetail> loadEndpoint(SearchBuilder sourceBuilder, String indexName,
                                               DetectPoint detectPoint) {
        sourceBuilder.aggregation(
            Aggregation.terms(Metrics.ENTITY_ID).field(Metrics.ENTITY_ID).size(1000));

        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(indexName);
        final SearchResponse response = getClient().search(index, sourceBuilder.build());

        final List<Call.CallDetail> calls = new ArrayList<>();
        final Map<String, Object> entityTerms =
            (Map<String, Object>) response.getAggregations().get(Metrics.ENTITY_ID);
        final List<Map<String, Object>> buckets =
            (List<Map<String, Object>>) entityTerms.get("buckets");
        for (final Map<String, Object> entityBucket : buckets) {
            String entityId = (String) entityBucket.get("key");

            Call.CallDetail call = new Call.CallDetail();
            call.buildFromEndpointRelation(entityId, detectPoint);
            calls.add(call);
        }
        return calls;
    }

    private void setQueryCondition(SearchBuilder search, long startTB, long endTB,
                                   List<String> serviceIds) {
        final BoolQueryBuilder query =
            Query.bool()
                 .must(Query.range(ServiceRelationServerSideMetrics.TIME_BUCKET)
                            .gte(startTB)
                            .lte(endTB));

        final BoolQueryBuilder serviceIdBoolQuery = Query.bool();

        query.must(serviceIdBoolQuery);

        if (serviceIds.size() == 1) {
            serviceIdBoolQuery.should(
                Query.term(
                    ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID,
                    serviceIds.get(0)
                ));
            serviceIdBoolQuery.should(
                Query.term(
                    ServiceRelationServerSideMetrics.DEST_SERVICE_ID,
                    serviceIds.get(0)
                ));
        } else {
            serviceIdBoolQuery.should(
                Query.terms(
                    ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID,
                    serviceIds
                ));
            serviceIdBoolQuery.should(
                Query.terms(
                    ServiceRelationServerSideMetrics.DEST_SERVICE_ID,
                    serviceIds
                ));
        }
        search.query(query);
    }
}
