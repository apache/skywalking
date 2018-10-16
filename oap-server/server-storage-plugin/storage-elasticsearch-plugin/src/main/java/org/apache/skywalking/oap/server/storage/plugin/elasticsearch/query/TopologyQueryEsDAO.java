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
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.manual.endpointrelation.*;
import org.apache.skywalking.oap.server.core.analysis.manual.service.*;
import org.apache.skywalking.oap.server.core.analysis.manual.servicerelation.*;
import org.apache.skywalking.oap.server.core.query.entity.*;
import org.apache.skywalking.oap.server.core.source.*;
import org.apache.skywalking.oap.server.core.storage.DownSamplingModelNameBuilder;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.*;
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
            throw new UnexpectedException("Service id is null");
        }

        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.size(0);
        setQueryCondition(sourceBuilder, startTB, endTB, serviceIds);

        String indexName = DownSamplingModelNameBuilder.build(step, ServiceRelationServerSideIndicator.INDEX_NAME);
        return load(sourceBuilder, indexName, ServiceRelationServerSideIndicator.SOURCE_SERVICE_ID, ServiceRelationServerSideIndicator.DEST_SERVICE_ID, Source.Service);
    }

    @Override
    public List<Call> loadSpecifiedClientSideServiceRelations(Step step, long startTB, long endTB,
        List<Integer> serviceIds) throws IOException {
        if (CollectionUtils.isEmpty(serviceIds)) {
            throw new UnexpectedException("Service id is null");
        }

        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.size(0);
        setQueryCondition(sourceBuilder, startTB, endTB, serviceIds);

        String indexName = DownSamplingModelNameBuilder.build(step, ServiceRelationClientSideIndicator.INDEX_NAME);
        return load(sourceBuilder, indexName, ServiceRelationClientSideIndicator.SOURCE_SERVICE_ID, ServiceRelationClientSideIndicator.DEST_SERVICE_ID, Source.Service);
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

        return load(sourceBuilder, indexName, ServiceRelationServerSideIndicator.SOURCE_SERVICE_ID, ServiceRelationServerSideIndicator.DEST_SERVICE_ID, Source.Service);
    }

    @Override public List<Call> loadClientSideServiceRelations(Step step, long startTB, long endTB) throws IOException {
        String indexName = DownSamplingModelNameBuilder.build(step, ServiceRelationClientSideIndicator.INDEX_NAME);
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.query(QueryBuilders.rangeQuery(ServiceRelationServerSideIndicator.TIME_BUCKET).gte(startTB).lte(endTB));
        sourceBuilder.size(0);

        return load(sourceBuilder, indexName, ServiceRelationClientSideIndicator.SOURCE_SERVICE_ID, ServiceRelationClientSideIndicator.DEST_SERVICE_ID, Source.Service);
    }

    @Override public List<ServiceMapping> loadServiceMappings(Step step, long startTB, long endTB) throws IOException {
        String indexName = DownSamplingModelNameBuilder.build(step, ServiceMappingIndicator.INDEX_NAME);
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.query(QueryBuilders.rangeQuery(ServiceMappingIndicator.TIME_BUCKET).gte(startTB).lte(endTB));
        sourceBuilder.size(0);

        TermsAggregationBuilder sourceAggregation = AggregationBuilders.terms(ServiceMappingIndicator.SERVICE_ID).field(ServiceMappingIndicator.SERVICE_ID).size(1000);
        sourceAggregation.subAggregation(AggregationBuilders.terms(ServiceMappingIndicator.MAPPING_SERVICE_ID).field(ServiceMappingIndicator.MAPPING_SERVICE_ID).size(1000));
        sourceBuilder.aggregation(sourceAggregation);

        SearchResponse response = getClient().search(indexName, sourceBuilder);

        List<ServiceMapping> serviceMappings = new ArrayList<>();
        Terms serviceIdTerms = response.getAggregations().get(ServiceMappingIndicator.SERVICE_ID);
        for (Terms.Bucket serviceIdBucket : serviceIdTerms.getBuckets()) {
            Terms mappingServiceIdTerms = serviceIdBucket.getAggregations().get(ServiceMappingIndicator.MAPPING_SERVICE_ID);
            for (Terms.Bucket mappingServiceIdBucket : mappingServiceIdTerms.getBuckets()) {
                ServiceMapping serviceMapping = new ServiceMapping();
                serviceMapping.setServiceId(serviceIdBucket.getKeyAsNumber().intValue());
                serviceMapping.setMappingServiceId(mappingServiceIdBucket.getKeyAsNumber().intValue());
                serviceMappings.add(serviceMapping);
            }
        }
        return serviceMappings;
    }

    @Override
    public List<ServiceComponent> loadServiceComponents(Step step, long startTB, long endTB) throws IOException {
        String indexName = DownSamplingModelNameBuilder.build(step, ServiceComponentIndicator.INDEX_NAME);
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.query(QueryBuilders.rangeQuery(ServiceComponentIndicator.TIME_BUCKET).gte(startTB).lte(endTB));
        sourceBuilder.size(0);

        TermsAggregationBuilder sourceAggregation = AggregationBuilders.terms(ServiceComponentIndicator.SERVICE_ID).field(ServiceComponentIndicator.SERVICE_ID).size(1000);
        sourceAggregation.subAggregation(AggregationBuilders.terms(ServiceComponentIndicator.COMPONENT_ID).field(ServiceComponentIndicator.COMPONENT_ID).size(1000));
        sourceBuilder.aggregation(sourceAggregation);

        SearchResponse response = getClient().search(indexName, sourceBuilder);

        List<ServiceComponent> serviceComponents = new ArrayList<>();
        Terms serviceIdTerms = response.getAggregations().get(ServiceComponentIndicator.SERVICE_ID);
        for (Terms.Bucket serviceIdBucket : serviceIdTerms.getBuckets()) {
            Terms componentIdTerms = serviceIdBucket.getAggregations().get(ServiceComponentIndicator.COMPONENT_ID);
            for (Terms.Bucket componentIdBucket : componentIdTerms.getBuckets()) {
                ServiceComponent serviceComponent = new ServiceComponent();
                serviceComponent.setServiceId(serviceIdBucket.getKeyAsNumber().intValue());
                serviceComponent.setComponentId(componentIdBucket.getKeyAsNumber().intValue());
                serviceComponents.add(serviceComponent);
            }
        }
        return serviceComponents;
    }

    @Override
    public List<Call> loadSpecifiedDestOfServerSideEndpointRelations(Step step, long startTB, long endTB,
        int destEndpointId) throws IOException {
        String indexName = DownSamplingModelNameBuilder.build(step, EndpointRelationServerSideIndicator.INDEX_NAME);

        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.size(0);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(EndpointRelationServerSideIndicator.TIME_BUCKET).gte(startTB).lte(endTB));
        boolQuery.must().add(QueryBuilders.termQuery(EndpointRelationServerSideIndicator.DEST_ENDPOINT_ID, destEndpointId));
        sourceBuilder.query(boolQuery);

        return load(sourceBuilder, indexName, EndpointRelationServerSideIndicator.SOURCE_ENDPOINT_ID, EndpointRelationServerSideIndicator.DEST_ENDPOINT_ID, Source.Endpoint);
    }

    @Override
    public List<Call> loadSpecifiedSourceOfClientSideEndpointRelations(Step step, long startTB, long endTB,
        int sourceEndpointId) throws IOException {
        String indexName = DownSamplingModelNameBuilder.build(step, EndpointRelationClientSideIndicator.INDEX_NAME);

        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.size(0);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(EndpointRelationClientSideIndicator.TIME_BUCKET).gte(startTB).lte(endTB));
        boolQuery.must().add(QueryBuilders.termQuery(EndpointRelationClientSideIndicator.SOURCE_ENDPOINT_ID, sourceEndpointId));
        sourceBuilder.query(boolQuery);

        return load(sourceBuilder, indexName, EndpointRelationClientSideIndicator.SOURCE_ENDPOINT_ID, EndpointRelationClientSideIndicator.DEST_ENDPOINT_ID, Source.Endpoint);
    }

    private List<Call> load(SearchSourceBuilder sourceBuilder, String indexName, String sourceCName,
        String destCName, Source source) throws IOException {
        TermsAggregationBuilder sourceAggregation = AggregationBuilders.terms(sourceCName).field(sourceCName).size(1000);
        sourceAggregation.subAggregation(AggregationBuilders.terms(destCName).field(destCName).size(1000));
        sourceBuilder.aggregation(sourceAggregation);

        SearchResponse response = getClient().search(indexName, sourceBuilder);

        List<Call> calls = new ArrayList<>();
        Terms sourceTerms = response.getAggregations().get(sourceCName);
        for (Terms.Bucket sourceBucket : sourceTerms.getBuckets()) {
            Terms destTerms = sourceBucket.getAggregations().get(destCName);
            for (Terms.Bucket destBucket : destTerms.getBuckets()) {
                Call value = new Call();
                value.setSource(sourceBucket.getKeyAsNumber().intValue());
                value.setTarget(destBucket.getKeyAsNumber().intValue());
                switch (source) {
                    case Service:
                        value.setId(ServiceRelation.buildEntityId(value.getSource(), value.getTarget()));
                        break;
                    case Endpoint:
                        value.setId(EndpointRelation.buildEntityId(value.getSource(), value.getTarget()));
                        break;
                }
                calls.add(value);
            }
        }
        return calls;
    }

    enum Source {
        Service, Endpoint
    }
}
