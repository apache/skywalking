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
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.query.entity.Attribute;
import org.apache.skywalking.oap.server.core.query.entity.Database;
import org.apache.skywalking.oap.server.core.query.entity.Endpoint;
import org.apache.skywalking.oap.server.core.query.entity.LanguageTrans;
import org.apache.skywalking.oap.server.core.query.entity.Service;
import org.apache.skywalking.oap.server.core.query.entity.ServiceInstance;
import org.apache.skywalking.oap.server.core.register.EndpointInventory;
import org.apache.skywalking.oap.server.core.register.NodeType;
import org.apache.skywalking.oap.server.core.register.RegisterSource;
import org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.MatchCNameBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import static org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory.PropertyUtil.HOST_NAME;
import static org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory.PropertyUtil.IPV4S;
import static org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory.PropertyUtil.LANGUAGE;
import static org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory.PropertyUtil.OS_NAME;
import static org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory.PropertyUtil.PROCESS_NO;

/**
 * @author peng-yongsheng
 */
public class MetadataQueryEsDAO extends EsDAO implements IMetadataQueryDAO {
    private static final Gson GSON = new Gson();

    private final int queryMaxSize;

    public MetadataQueryEsDAO(ElasticSearchClient client, int queryMaxSize) {
        super(client);
        this.queryMaxSize = queryMaxSize;
    }

    @Override public int numOfService(long startTimestamp, long endTimestamp) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must().add(timeRangeQueryBuild(startTimestamp, endTimestamp));

        boolQueryBuilder.must().add(QueryBuilders.termQuery(ServiceInventory.IS_ADDRESS, BooleanUtils.FALSE));

        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(0);

        SearchResponse response = getClient().search(ServiceInventory.INDEX_NAME, sourceBuilder);
        return (int)response.getHits().getTotalHits();
    }

    @Override public int numOfEndpoint() throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        boolQueryBuilder.must().add(QueryBuilders.termQuery(EndpointInventory.DETECT_POINT, DetectPoint.SERVER.ordinal()));

        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(0);

        SearchResponse response = getClient().search(EndpointInventory.INDEX_NAME, sourceBuilder);
        return (int)response.getHits().getTotalHits();
    }

    @Override
    public int numOfConjectural(int nodeTypeValue) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        sourceBuilder.query(QueryBuilders.termQuery(ServiceInventory.NODE_TYPE, nodeTypeValue));
        sourceBuilder.size(0);

        SearchResponse response = getClient().search(ServiceInventory.INDEX_NAME, sourceBuilder);

        return (int)response.getHits().getTotalHits();
    }

    @Override
    public List<Service> getAllServices(long startTimestamp, long endTimestamp) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must().add(timeRangeQueryBuild(startTimestamp, endTimestamp));

        boolQueryBuilder.must().add(QueryBuilders.termQuery(ServiceInventory.IS_ADDRESS, BooleanUtils.FALSE));

        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(queryMaxSize);

        SearchResponse response = getClient().search(ServiceInventory.INDEX_NAME, sourceBuilder);

        return buildServices(response);
    }

    @Override
    public List<Database> getAllDatabases() throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must().add(QueryBuilders.termQuery(ServiceInventory.NODE_TYPE, NodeType.Database.value()));

        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(queryMaxSize);

        SearchResponse response = getClient().search(ServiceInventory.INDEX_NAME, sourceBuilder);

        List<Database> databases = new ArrayList<>();
        for (SearchHit searchHit : response.getHits()) {
            Map<String, Object> sourceAsMap = searchHit.getSourceAsMap();
            Database database = new Database();
            database.setId(((Number)sourceAsMap.get(ServiceInventory.SEQUENCE)).intValue());
            database.setName((String)sourceAsMap.get(ServiceInventory.NAME));
            String propertiesString = (String)sourceAsMap.get(ServiceInstanceInventory.PROPERTIES);
            if (!Strings.isNullOrEmpty(propertiesString)) {
                JsonObject properties = GSON.fromJson(propertiesString, JsonObject.class);
                if (properties.has(ServiceInventory.PropertyUtil.DATABASE)) {
                    database.setType(properties.get(ServiceInventory.PropertyUtil.DATABASE).getAsString());
                } else {
                    database.setType("UNKNOWN");
                }
            }
            databases.add(database);
        }
        return databases;
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
        sourceBuilder.size(queryMaxSize);

        SearchResponse response = getClient().search(ServiceInventory.INDEX_NAME, sourceBuilder);
        return buildServices(response);
    }

    @Override
    public Service searchService(String serviceCode) throws IOException {
        GetResponse response = getClient().get(ServiceInventory.INDEX_NAME, ServiceInventory.buildId(serviceCode));
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

        SearchResponse response = getClient().search(EndpointInventory.INDEX_NAME, sourceBuilder);

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
        sourceBuilder.size(queryMaxSize);

        SearchResponse response = getClient().search(ServiceInstanceInventory.INDEX_NAME, sourceBuilder);

        List<ServiceInstance> serviceInstances = new ArrayList<>();
        for (SearchHit searchHit : response.getHits()) {
            Map<String, Object> sourceAsMap = searchHit.getSourceAsMap();

            ServiceInstance serviceInstance = new ServiceInstance();
            serviceInstance.setId(String.valueOf(sourceAsMap.get(ServiceInstanceInventory.SEQUENCE)));
            serviceInstance.setName((String)sourceAsMap.get(ServiceInstanceInventory.NAME));
            serviceInstance.setInstanceUUID((String)sourceAsMap.get(ServiceInstanceInventory.INSTANCE_UUID));

            String propertiesString = (String)sourceAsMap.get(ServiceInstanceInventory.PROPERTIES);
            if (!Strings.isNullOrEmpty(propertiesString)) {
                JsonObject properties = GSON.fromJson(propertiesString, JsonObject.class);
                for (Map.Entry<String, JsonElement> property : properties.entrySet()) {
                    String key = property.getKey();
                    String value = property.getValue().getAsString();
                    if (key.equals(LANGUAGE)) {
                        serviceInstance.setLanguage(LanguageTrans.INSTANCE.value(value));
                    } else if (key.equals(OS_NAME)) {
                        serviceInstance.getAttributes().add(new Attribute(OS_NAME, value));
                    } else if (key.equals(HOST_NAME)) {
                        serviceInstance.getAttributes().add(new Attribute(HOST_NAME, value));
                    } else if (key.equals(PROCESS_NO)) {
                        serviceInstance.getAttributes().add(new Attribute(PROCESS_NO, value));
                    } else if (key.equals(IPV4S)) {
                        List<String> ipv4s = ServiceInstanceInventory.PropertyUtil.ipv4sDeserialize(properties.get(IPV4S).getAsString());
                        for (String ipv4 : ipv4s) {
                            serviceInstance.getAttributes().add(new Attribute(ServiceInstanceInventory.PropertyUtil.IPV4S, ipv4));
                        }
                    } else {
                        serviceInstance.getAttributes().add(new Attribute(key, value));
                    }
                }
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

    protected BoolQueryBuilder timeRangeQueryBuild(long startTimestamp, long endTimestamp) {
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
