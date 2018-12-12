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

import com.google.common.base.Strings;
import java.io.IOException;
import java.util.*;
import org.apache.skywalking.oap.server.core.query.entity.*;
import org.apache.skywalking.oap.server.core.register.*;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.*;
import org.elasticsearch.action.get.GetResponse;
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
        boolQueryBuilder.must().add(timeRangeQueryBuild(startTimestamp, endTimestamp));

        boolQueryBuilder.must().add(QueryBuilders.termQuery(ServiceInventory.IS_ADDRESS, BooleanUtils.FALSE));

        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(0);

        SearchResponse response = getClient().search(ServiceInventory.MODEL_NAME, sourceBuilder);
        return (int)response.getHits().getTotalHits();
    }

    @Override public int numOfEndpoint(long startTimestamp, long endTimestamp) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        boolQueryBuilder.must().add(QueryBuilders.termQuery(EndpointInventory.DETECT_POINT, DetectPoint.SERVER.ordinal()));

        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(0);

        SearchResponse response = getClient().search(EndpointInventory.MODEL_NAME, sourceBuilder);
        return (int)response.getHits().getTotalHits();
    }

    @Override public int numOfConjectural(long startTimestamp, long endTimestamp, int srcLayer) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        sourceBuilder.query(QueryBuilders.termQuery(NetworkAddressInventory.SRC_LAYER, srcLayer));
        sourceBuilder.size(0);

        SearchResponse response = getClient().search(NetworkAddressInventory.MODEL_NAME, sourceBuilder);

        return (int)response.getHits().getTotalHits();
    }

    @Override
    public List<Service> getAllServices(long startTimestamp, long endTimestamp) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must().add(timeRangeQueryBuild(startTimestamp, endTimestamp));

        boolQueryBuilder.must().add(QueryBuilders.termQuery(ServiceInventory.IS_ADDRESS, BooleanUtils.FALSE));

        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(100);

        SearchResponse response = getClient().search(ServiceInventory.MODEL_NAME, sourceBuilder);

        return buildServices(response);
    }

    @Override public List<Service> searchServices(long startTimestamp, long endTimestamp,
        String keyword) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must().add(timeRangeQueryBuild(startTimestamp, endTimestamp));
        boolQueryBuilder.must().add(QueryBuilders.termQuery(ServiceInventory.IS_ADDRESS, BooleanUtils.FALSE));

        if (!Strings.isNullOrEmpty(keyword)) {
            String matchCName = MatchCNameBuilder.INSTANCE.build(ServiceInventory.NAME);
            boolQueryBuilder.must().add(QueryBuilders.matchQuery(matchCName, keyword));
        }

        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(100);

        SearchResponse response = getClient().search(ServiceInventory.MODEL_NAME, sourceBuilder);
        return buildServices(response);
    }

    @Override
    public Service searchService(String serviceCode) throws IOException {
        GetResponse response = getClient().get(ServiceInventory.MODEL_NAME, ServiceInventory.buildId(serviceCode));
        if (response.isExists()) {
            Service service = new Service();
            service.setId(((Number)response.getSource().get(ServiceInventory.SEQUENCE)).intValue());
            service.setName((String)response.getSource().get(ServiceInventory.NAME));
            return service;
        } else {
            return null;
        }
    }

    @Override public List<Endpoint> searchEndpoint(String keyword, String serviceId,
        int limit) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must().add(QueryBuilders.termQuery(EndpointInventory.SERVICE_ID, serviceId));

        if (!Strings.isNullOrEmpty(keyword)) {
            String matchCName = MatchCNameBuilder.INSTANCE.build(EndpointInventory.NAME);
            boolQueryBuilder.must().add(QueryBuilders.matchQuery(matchCName, keyword));
        }

        boolQueryBuilder.must().add(QueryBuilders.termQuery(EndpointInventory.DETECT_POINT, DetectPoint.SERVER.ordinal()));

        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(limit);

        SearchResponse response = getClient().search(EndpointInventory.MODEL_NAME, sourceBuilder);

        List<Endpoint> endpoints = new ArrayList<>();
        for (SearchHit searchHit : response.getHits()) {
            Map<String, Object> sourceAsMap = searchHit.getSourceAsMap();

            Endpoint endpoint = new Endpoint();
            endpoint.setId(((Number)sourceAsMap.get(EndpointInventory.SEQUENCE)).intValue());
            endpoint.setName((String)sourceAsMap.get(EndpointInventory.NAME));
            endpoints.add(endpoint);
        }

        return endpoints;
    }

    @Override public List<ServiceInstance> getServiceInstances(long startTimestamp, long endTimestamp,
        String serviceId) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must().add(timeRangeQueryBuild(startTimestamp, endTimestamp));

        boolQueryBuilder.must().add(QueryBuilders.termQuery(ServiceInstanceInventory.SERVICE_ID, serviceId));

        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(100);

        SearchResponse response = getClient().search(ServiceInstanceInventory.MODEL_NAME, sourceBuilder);

        List<ServiceInstance> serviceInstances = new ArrayList<>();
        for (SearchHit searchHit : response.getHits()) {
            Map<String, Object> sourceAsMap = searchHit.getSourceAsMap();

            ServiceInstance serviceInstance = new ServiceInstance();
            serviceInstance.setId(String.valueOf(sourceAsMap.get(ServiceInstanceInventory.SEQUENCE)));
            serviceInstance.setName((String)sourceAsMap.get(ServiceInstanceInventory.NAME));
            int languageId = ((Number)sourceAsMap.get(ServiceInstanceInventory.LANGUAGE)).intValue();
            serviceInstance.setLanguage(LanguageTrans.INSTANCE.value(languageId));

            String osName = (String)sourceAsMap.get(ServiceInstanceInventory.OS_NAME);
            if (!Strings.isNullOrEmpty(osName)) {
                serviceInstance.getAttributes().add(new Attribute(ServiceInstanceInventory.OS_NAME, osName));
            }
            String hostName = (String)sourceAsMap.get(ServiceInstanceInventory.HOST_NAME);
            if (!Strings.isNullOrEmpty(hostName)) {
                serviceInstance.getAttributes().add(new Attribute(ServiceInstanceInventory.HOST_NAME, hostName));
            }
            serviceInstance.getAttributes().add(new Attribute(ServiceInstanceInventory.PROCESS_NO, String.valueOf(((Number)sourceAsMap.get(ServiceInstanceInventory.PROCESS_NO)).intValue())));

            List<String> ipv4s = ServiceInstanceInventory.AgentOsInfo.ipv4sDeserialize((String)sourceAsMap.get(ServiceInstanceInventory.IPV4S));
            for (String ipv4 : ipv4s) {
                serviceInstance.getAttributes().add(new Attribute(ServiceInstanceInventory.IPV4S, ipv4));
            }
            serviceInstances.add(serviceInstance);
        }

        return serviceInstances;
    }

    private List<Service> buildServices(SearchResponse response) {
        List<Service> services = new ArrayList<>();
        for (SearchHit searchHit : response.getHits()) {
            Map<String, Object> sourceAsMap = searchHit.getSourceAsMap();

            Service service = new Service();
            service.setId(((Number)sourceAsMap.get(ServiceInventory.SEQUENCE)).intValue());
            service.setName((String)sourceAsMap.get(ServiceInventory.NAME));
            services.add(service);
        }

        return services;
    }

    private BoolQueryBuilder timeRangeQueryBuild(long startTimestamp, long endTimestamp) {
        BoolQueryBuilder boolQuery1 = QueryBuilders.boolQuery();
        boolQuery1.must().add(QueryBuilders.rangeQuery(RegisterSource.HEARTBEAT_TIME).gte(endTimestamp));
        boolQuery1.must().add(QueryBuilders.rangeQuery(RegisterSource.REGISTER_TIME).lte(endTimestamp));

        BoolQueryBuilder boolQuery2 = QueryBuilders.boolQuery();
        boolQuery2.must().add(QueryBuilders.rangeQuery(RegisterSource.REGISTER_TIME).lte(endTimestamp));
        boolQuery2.must().add(QueryBuilders.rangeQuery(RegisterSource.HEARTBEAT_TIME).gte(startTimestamp));

        BoolQueryBuilder timeBoolQuery = QueryBuilders.boolQuery();
        timeBoolQuery.should().add(boolQuery1);
        timeBoolQuery.should().add(boolQuery2);

        return timeBoolQuery;
    }
}
