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
import java.util.Map;
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.aggregation.Aggregation;
import org.apache.skywalking.library.elasticsearch.requests.search.aggregation.TermsAggregationBuilder;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.endpoint.EndpointRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.process.ProcessRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.process.ProcessRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntList;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.input.Duration;
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
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(Duration duration, List<String> serviceIds) {
        if (CollectionUtils.isEmpty(serviceIds)) {
            throw new UnexpectedException("Service id is empty");
        }

        final SearchBuilder sourceBuilder = Search.builder().size(0);
        setQueryCondition(sourceBuilder, duration, serviceIds, ServiceRelationServerSideMetrics.INDEX_NAME);

        return buildServiceRelation(
            sourceBuilder, ServiceRelationServerSideMetrics.INDEX_NAME, DetectPoint.SERVER);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(Duration duration,
                                                                         List<String> serviceIds) {
        if (CollectionUtils.isEmpty(serviceIds)) {
            throw new UnexpectedException("Service id is empty");
        }

        final SearchBuilder sourceBuilder = Search.builder().size(0);
        setQueryCondition(sourceBuilder, duration, serviceIds, ServiceRelationClientSideMetrics.INDEX_NAME);

        return buildServiceRelation(
            sourceBuilder, ServiceRelationClientSideMetrics.INDEX_NAME, DetectPoint.CLIENT);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(Duration duration) {
        SearchBuilder sourceBuilder = Search.builder();
        final BoolQueryBuilder query = Query.bool()
                                            .must(Query.range(ServiceRelationServerSideMetrics.TIME_BUCKET)
                                                       .gte(duration.getStartTimeBucket())
                                                       .lte(duration.getEndTimeBucket()));
        if (IndexController.LogicIndicesRegister.isMergedTable(ServiceRelationServerSideMetrics.INDEX_NAME)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.METRIC_TABLE_NAME,
                                  ServiceRelationServerSideMetrics.INDEX_NAME
            ));
        }
        sourceBuilder.query(query).size(0);

        return buildServiceRelation(
            sourceBuilder, ServiceRelationServerSideMetrics.INDEX_NAME, DetectPoint.SERVER);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(Duration duration) {
        SearchBuilder sourceBuilder = Search.builder();
        final BoolQueryBuilder query = Query.bool()
                                            .must(Query.range(ServiceRelationClientSideMetrics.TIME_BUCKET)
                                                       .gte(duration.getStartTimeBucket())
                                                       .lte(duration.getEndTimeBucket()));
        if (IndexController.LogicIndicesRegister.isMergedTable(ServiceRelationClientSideMetrics.INDEX_NAME)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.METRIC_TABLE_NAME,
                                  ServiceRelationClientSideMetrics.INDEX_NAME
            ));
        }
        sourceBuilder.query(query).size(0);

        return buildServiceRelation(
            sourceBuilder, ServiceRelationClientSideMetrics.INDEX_NAME, DetectPoint.CLIENT);
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtServerSide(String clientServiceId,
                                                                          String serverServiceId,
                                                                          Duration duration) {
        final SearchBuilder search = Search.builder().size(0);
        setInstanceQueryCondition(
            search, duration, clientServiceId, serverServiceId, ServiceInstanceRelationServerSideMetrics.INDEX_NAME);

        return buildInstanceRelation(
            search, ServiceInstanceRelationServerSideMetrics.INDEX_NAME, DetectPoint.SERVER);
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtClientSide(String clientServiceId,
                                                                          String serverServiceId,
                                                                          Duration duration) {
        final SearchBuilder search = Search.builder().size(0);
        setInstanceQueryCondition(
            search, duration, clientServiceId, serverServiceId, ServiceInstanceRelationClientSideMetrics.INDEX_NAME);

        return buildInstanceRelation(
            search, ServiceInstanceRelationClientSideMetrics.INDEX_NAME, DetectPoint.CLIENT);
    }

    private void setInstanceQueryCondition(SearchBuilder search, Duration duration,
                                           String clientServiceId, String serverServiceId, String indexName) {
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
                            .gte(duration.getStartTimeBucket())
                            .lte(duration.getEndTimeBucket()))
                 .must(serviceIdBoolQuery);
        if (IndexController.LogicIndicesRegister.isMergedTable(indexName)) {
            boolQuery.must(Query.term(IndexController.LogicIndicesRegister.METRIC_TABLE_NAME, indexName));
        }
        search.query(boolQuery);
    }

    @Override
    public List<Call.CallDetail> loadEndpointRelation(Duration duration, String destEndpointId) {
        SearchBuilder sourceBuilder = Search.builder();
        sourceBuilder.size(0);

        BoolQueryBuilder boolQuery = Query.bool();
        boolQuery.must(
            Query.range(EndpointRelationServerSideMetrics.TIME_BUCKET)
                 .gte(duration.getStartTimeBucket()).lte(duration.getEndTimeBucket()));

        BoolQueryBuilder serviceIdBoolQuery = Query.bool();
        boolQuery.must(serviceIdBoolQuery);
        serviceIdBoolQuery.should(
            Query.term(
                EndpointRelationServerSideMetrics.SOURCE_ENDPOINT, destEndpointId
            ));
        serviceIdBoolQuery.should(
            Query.term(
                EndpointRelationServerSideMetrics.DEST_ENDPOINT, destEndpointId
            ));
        if (IndexController.LogicIndicesRegister.isMergedTable(EndpointRelationServerSideMetrics.INDEX_NAME)) {
            boolQuery.must(Query.term(IndexController.LogicIndicesRegister.METRIC_TABLE_NAME,
                                      EndpointRelationServerSideMetrics.INDEX_NAME
            ));
        }
        sourceBuilder.query(boolQuery);

        return loadEndpoint(
            sourceBuilder, EndpointRelationServerSideMetrics.INDEX_NAME, DetectPoint.SERVER);
    }

    @Override
    public List<Call.CallDetail> loadProcessRelationDetectedAtClientSide(String serviceInstanceId,
                                                                         Duration duration) throws IOException {
        return buildProcessRelation(serviceInstanceId, duration, DetectPoint.CLIENT);
    }

    @Override
    public List<Call.CallDetail> loadProcessRelationDetectedAtServerSide(String serviceInstanceId,
                                                                         Duration duration) throws IOException {
        return buildProcessRelation(serviceInstanceId, duration, DetectPoint.SERVER);
    }

    private List<Call.CallDetail> buildProcessRelation(String serviceInstanceId,
                                                       Duration duration,
                                                       DetectPoint detectPoint) throws IOException {
        final SearchBuilder sourceBuilder = Search.builder().size(0);
        final BoolQueryBuilder query = Query.bool()
                                            .must(Query.term(ProcessRelationServerSideMetrics.SERVICE_INSTANCE_ID,
                                                             serviceInstanceId
                                            ))
                                            .must(Query.range(EndpointRelationServerSideMetrics.TIME_BUCKET)
                                                       .gte(duration.getStartTimeBucket())
                                                       .lte(duration.getEndTimeBucket()));
        sourceBuilder.query(query);
        sourceBuilder.aggregation(
            Aggregation
                .terms(Metrics.ENTITY_ID).field(Metrics.ENTITY_ID)
                .subAggregation(
                    Aggregation.terms(ProcessRelationServerSideMetrics.COMPONENT_ID)
                               .field(ProcessRelationServerSideMetrics.COMPONENT_ID)
                               .executionHint(TermsAggregationBuilder.ExecutionHint.MAP)
                               .collectMode(TermsAggregationBuilder.CollectMode.BREADTH_FIRST))
                .executionHint(TermsAggregationBuilder.ExecutionHint.MAP)
                .collectMode(TermsAggregationBuilder.CollectMode.BREADTH_FIRST)
                .size(1000));

        String indexName = detectPoint == DetectPoint.SERVER ?
            ProcessRelationServerSideMetrics.INDEX_NAME : ProcessRelationClientSideMetrics.INDEX_NAME;

        final String index = IndexController.LogicIndicesRegister.getPhysicalTableName(indexName);
        if (IndexController.LogicIndicesRegister.isMergedTable(indexName)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.METRIC_TABLE_NAME, indexName));
        }
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
                    ProcessRelationServerSideMetrics.COMPONENT_ID);
            final List<Map<String, Object>> subAgg =
                (List<Map<String, Object>>) componentTerms.get("buckets");
            final int componentId = ((Number) subAgg.iterator().next().get("key")).intValue();

            Call.CallDetail call = new Call.CallDetail();
            call.buildProcessRelation(entityId, componentId, detectPoint);
            calls.add(call);
        }
        return calls;
    }

    private List<Call.CallDetail> buildServiceRelation(SearchBuilder sourceBuilder,
                                                       String indexName,
                                                       DetectPoint detectPoint) {
        sourceBuilder.aggregation(
            Aggregation
                .terms(Metrics.ENTITY_ID).field(Metrics.ENTITY_ID)
                .subAggregation(
                    Aggregation.terms(ServiceRelationServerSideMetrics.COMPONENT_IDS)
                               .field(ServiceRelationServerSideMetrics.COMPONENT_IDS)
                               .executionHint(TermsAggregationBuilder.ExecutionHint.MAP)
                               .collectMode(TermsAggregationBuilder.CollectMode.BREADTH_FIRST))
                .executionHint(TermsAggregationBuilder.ExecutionHint.MAP)
                .collectMode(TermsAggregationBuilder.CollectMode.BREADTH_FIRST)
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
                    ServiceRelationServerSideMetrics.COMPONENT_IDS);
            final List<Map<String, Object>> subAgg =
                (List<Map<String, Object>>) componentTerms.get("buckets");
            final IntList componentIds = new IntList((String) subAgg.iterator().next().get("key"));

            Call.CallDetail call = new Call.CallDetail();
            for (int i = 0; i < componentIds.size(); i++) {
                call.buildFromServiceRelation(entityId, componentIds.get(i), detectPoint);
                calls.add(call);
            }
        }
        return calls;
    }

    private List<Call.CallDetail> buildInstanceRelation(SearchBuilder sourceBuilder,
                                                        String indexName,
                                                        DetectPoint detectPoint) {
        sourceBuilder.aggregation(
            Aggregation
                .terms(Metrics.ENTITY_ID).field(Metrics.ENTITY_ID)
                .executionHint(TermsAggregationBuilder.ExecutionHint.MAP)
                .collectMode(TermsAggregationBuilder.CollectMode.BREADTH_FIRST)
                .size(1000));

        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(indexName);
        SearchResponse response = getClient().search(index, sourceBuilder.build());

        List<Call.CallDetail> calls = new ArrayList<>();
        final Map<String, Object> entityTerms =
            (Map<String, Object>) response.getAggregations().get(Metrics.ENTITY_ID);

        final List<Map<String, Object>> buckets =
            (List<Map<String, Object>>) entityTerms.get("buckets");
        for (final Map<String, Object> entityBucket : buckets) {
            String entityId = (String) entityBucket.get("key");

            Call.CallDetail call = new Call.CallDetail();
            call.buildFromInstanceRelation(entityId, detectPoint);
            calls.add(call);
        }
        return calls;
    }

    private List<Call.CallDetail> loadEndpoint(SearchBuilder sourceBuilder, String indexName,
                                               DetectPoint detectPoint) {
        sourceBuilder.aggregation(
            Aggregation.terms(Metrics.ENTITY_ID).field(Metrics.ENTITY_ID)
                       .executionHint(TermsAggregationBuilder.ExecutionHint.MAP)
                       .collectMode(TermsAggregationBuilder.CollectMode.BREADTH_FIRST)
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

            Call.CallDetail call = new Call.CallDetail();
            call.buildFromEndpointRelation(entityId, detectPoint);
            calls.add(call);
        }
        return calls;
    }

    private void setQueryCondition(SearchBuilder search, Duration duration,
                                   List<String> serviceIds, String indexName) {
        final BoolQueryBuilder query =
            Query.bool()
                 .must(Query.range(ServiceRelationServerSideMetrics.TIME_BUCKET)
                            .gte(duration.getStartTimeBucket())
                            .lte(duration.getEndTimeBucket()));

        final BoolQueryBuilder serviceIdBoolQuery = Query.bool();

        query.must(serviceIdBoolQuery);
        if (IndexController.LogicIndicesRegister.isMergedTable(indexName)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.METRIC_TABLE_NAME, indexName));
        }
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
