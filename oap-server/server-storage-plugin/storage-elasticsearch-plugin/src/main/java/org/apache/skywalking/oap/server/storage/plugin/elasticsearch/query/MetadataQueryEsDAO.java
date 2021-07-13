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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.query.enumeration.Language;
import org.apache.skywalking.oap.server.core.query.type.Attribute;
import org.apache.skywalking.oap.server.core.query.type.Database;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.MatchCNameBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import static org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic.PropertyUtil.LANGUAGE;

public class MetadataQueryEsDAO extends EsDAO implements IMetadataQueryDAO {
    private final int queryMaxSize;

    public MetadataQueryEsDAO(ElasticSearchClient client, int queryMaxSize) {
        super(client);
        this.queryMaxSize = queryMaxSize;
    }

    @Override
    public List<Service> getAllServices(final String group) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must().add(QueryBuilders.termQuery(ServiceTraffic.NODE_TYPE, NodeType.Normal.value()));
        if (StringUtil.isNotEmpty(group)) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(ServiceTraffic.GROUP, group));
        }

        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(queryMaxSize);

        SearchResponse response = getClient()
            .search(IndexController.LogicIndicesRegister.getPhysicalTableName(ServiceTraffic.INDEX_NAME), sourceBuilder);

        return buildServices(response);
    }

    @Override
    public List<Service> getAllBrowserServices() throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must().add(QueryBuilders.termQuery(ServiceTraffic.NODE_TYPE, NodeType.Browser.value()));

        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(queryMaxSize);

        SearchResponse response = getClient()
            .search(IndexController.LogicIndicesRegister.getPhysicalTableName(ServiceTraffic.INDEX_NAME), sourceBuilder);

        return buildServices(response);
    }

    @Override
    public List<Database> getAllDatabases() throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must().add(QueryBuilders.termQuery(ServiceTraffic.NODE_TYPE, NodeType.Database.value()));

        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(queryMaxSize);

        SearchResponse response = getClient()
            .search(IndexController.LogicIndicesRegister.getPhysicalTableName(ServiceTraffic.INDEX_NAME), sourceBuilder);

        final List<Service> serviceList = buildServices(response);
        List<Database> databases = new ArrayList<>();
        for (Service service : serviceList) {
            Database database = new Database();
            database.setId(service.getId());
            database.setName(service.getName());
            databases.add(database);
        }
        return databases;
    }

    @Override
    public List<Service> searchServices(String keyword) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        boolQueryBuilder.must().add(QueryBuilders.termQuery(ServiceTraffic.NODE_TYPE, NodeType.Normal.value()));
        if (!Strings.isNullOrEmpty(keyword)) {
            String matchCName = MatchCNameBuilder.INSTANCE.build(ServiceTraffic.NAME);
            boolQueryBuilder.must().add(QueryBuilders.matchQuery(matchCName, keyword));
        }

        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(queryMaxSize);

        SearchResponse response = getClient()
            .search(IndexController.LogicIndicesRegister.getPhysicalTableName(ServiceTraffic.INDEX_NAME), sourceBuilder);
        return buildServices(response);
    }

    @Override
    public Service searchService(String serviceCode) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must().add(QueryBuilders.termQuery(ServiceTraffic.NODE_TYPE, NodeType.Normal.value()));
        boolQueryBuilder.must().add(QueryBuilders.termQuery(ServiceTraffic.NAME, serviceCode));
        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(1);
        SearchResponse response = getClient()
            .search(IndexController.LogicIndicesRegister.getPhysicalTableName(ServiceTraffic.INDEX_NAME), sourceBuilder);
        final List<Service> services = buildServices(response);
        return services.size() > 0 ? services.get(0) : null;
    }

    @Override
    public List<Endpoint> searchEndpoint(String keyword, String serviceId, int limit) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must().add(QueryBuilders.termQuery(EndpointTraffic.SERVICE_ID, serviceId));

        if (!Strings.isNullOrEmpty(keyword)) {
            String matchCName = MatchCNameBuilder.INSTANCE.build(EndpointTraffic.NAME);
            boolQueryBuilder.must().add(QueryBuilders.matchQuery(matchCName, keyword));
        }

        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(limit);

        SearchResponse response = getClient()
            .search(IndexController.LogicIndicesRegister.getPhysicalTableName(EndpointTraffic.INDEX_NAME), sourceBuilder);

        List<Endpoint> endpoints = new ArrayList<>();
        for (SearchHit searchHit : response.getHits()) {
            Map<String, Object> sourceAsMap = searchHit.getSourceAsMap();

            final EndpointTraffic endpointTraffic = new EndpointTraffic.Builder().storage2Entity(sourceAsMap);

            Endpoint endpoint = new Endpoint();
            endpoint.setId(endpointTraffic.id());
            endpoint.setName((String) sourceAsMap.get(EndpointTraffic.NAME));
            endpoints.add(endpoint);
        }

        return endpoints;
    }

    @Override
    public List<ServiceInstance> getServiceInstances(long startTimestamp, long endTimestamp,
                                                     String serviceId) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        final long minuteTimeBucket = TimeBucket.getMinuteTimeBucket(startTimestamp);

        boolQueryBuilder.must()
                        .add(QueryBuilders.rangeQuery(InstanceTraffic.LAST_PING_TIME_BUCKET).gte(minuteTimeBucket));
        boolQueryBuilder.must().add(QueryBuilders.termQuery(InstanceTraffic.SERVICE_ID, serviceId));

        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(queryMaxSize);

        SearchResponse response = getClient()
            .search(IndexController.LogicIndicesRegister.getPhysicalTableName(InstanceTraffic.INDEX_NAME), sourceBuilder);

        List<ServiceInstance> serviceInstances = new ArrayList<>();
        for (SearchHit searchHit : response.getHits()) {
            Map<String, Object> sourceAsMap = searchHit.getSourceAsMap();

            final InstanceTraffic instanceTraffic = new InstanceTraffic.Builder().storage2Entity(sourceAsMap);

            ServiceInstance serviceInstance = new ServiceInstance();
            serviceInstance.setId(instanceTraffic.id());
            serviceInstance.setName(instanceTraffic.getName());
            serviceInstance.setInstanceUUID(serviceInstance.getId());

            JsonObject properties = instanceTraffic.getProperties();
            if (properties != null) {
                for (Map.Entry<String, JsonElement> property : properties.entrySet()) {
                    String key = property.getKey();
                    String value = property.getValue().getAsString();
                    if (key.equals(LANGUAGE)) {
                        serviceInstance.setLanguage(Language.value(value));
                    } else {
                        serviceInstance.getAttributes().add(new Attribute(key, value));
                    }
                }
            } else {
                serviceInstance.setLanguage(Language.UNKNOWN);
            }
            serviceInstances.add(serviceInstance);
        }
        return serviceInstances;
    }

    private List<Service> buildServices(SearchResponse response) {
        List<Service> services = new ArrayList<>();
        for (SearchHit searchHit : response.getHits()) {
            Map<String, Object> sourceAsMap = searchHit.getSourceAsMap();

            final ServiceTraffic.Builder builder = new ServiceTraffic.Builder();
            final ServiceTraffic serviceTraffic = builder.storage2Entity(sourceAsMap);

            Service service = new Service();
            service.setId(serviceTraffic.id());
            service.setName(serviceTraffic.getName());
            service.setGroup(serviceTraffic.getGroup());
            services.add(service);
        }

        return services;
    }
}
