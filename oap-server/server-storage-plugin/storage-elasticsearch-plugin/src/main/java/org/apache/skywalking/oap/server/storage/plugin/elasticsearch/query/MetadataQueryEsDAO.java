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
import java.util.stream.Collectors;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
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

import static org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic.PropertyUtil.LANGUAGE;

public class MetadataQueryEsDAO extends EsDAO implements IMetadataQueryDAO {
    private final int queryMaxSize;

    public MetadataQueryEsDAO(ElasticSearchClient client, int queryMaxSize) {
        super(client);
        this.queryMaxSize = queryMaxSize;
    }

    @Override
    public List<Service> getAllServices(final String group) throws IOException {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(ServiceTraffic.INDEX_NAME);

        final BoolQueryBuilder query =
            Query.bool()
                 .must(Query.term(ServiceTraffic.NODE_TYPE, NodeType.Normal.value()));
        final SearchBuilder search = Search.builder().query(query).size(queryMaxSize);
        if (StringUtil.isNotEmpty(group)) {
            query.must(Query.term(ServiceTraffic.GROUP, group));
        }
        final SearchResponse results = getClient().search(index, search.build());

        return buildServices(results);
    }

    @Override
    public List<Service> getAllBrowserServices() throws IOException {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(ServiceTraffic.INDEX_NAME);
        final BoolQueryBuilder query = Query.bool().must(
            Query.term(ServiceTraffic.NODE_TYPE, NodeType.Browser.value()));
        final SearchBuilder search = Search.builder().query(query).size(queryMaxSize);
        final SearchResponse result = getClient().search(index, search.build());

        return buildServices(result);
    }

    @Override
    public List<Database> getAllDatabases() throws IOException {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(ServiceTraffic.INDEX_NAME);

        final BoolQueryBuilder query = Query.bool().must(
            Query.term(ServiceTraffic.NODE_TYPE, NodeType.Database.value()));
        final SearchBuilder search = Search.builder().query(query).size(queryMaxSize);
        final SearchResponse results = getClient().search(index, search.build());

        final List<Service> serviceList = buildServices(results);
        return serviceList.stream().map(service -> {
            Database database = new Database();
            database.setId(service.getId());
            database.setName(service.getName());
            return database;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Service> searchServices(String keyword) throws IOException {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(ServiceTraffic.INDEX_NAME);

        final BoolQueryBuilder query =
            Query.bool()
                 .must(Query.term(ServiceTraffic.NODE_TYPE, NodeType.Normal.value()));
        final SearchBuilder search = Search.builder().query(query).size(queryMaxSize);

        if (!Strings.isNullOrEmpty(keyword)) {
            String matchCName = MatchCNameBuilder.INSTANCE.build(ServiceTraffic.NAME);
            query.must(Query.match(matchCName, keyword));
        }

        SearchResponse response = getClient().search(index, search.build());
        return buildServices(response);
    }

    @Override
    public Service searchService(String serviceCode) throws IOException {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(ServiceTraffic.INDEX_NAME);
        final BoolQueryBuilder query =
            Query.bool()
                 .must(Query.term(ServiceTraffic.NODE_TYPE, NodeType.Normal.value()))
                 .must(Query.term(ServiceTraffic.NAME, serviceCode));
        final SearchBuilder search = Search.builder().query(query).size(1);

        final SearchResponse response = getClient().search(index, search.build());
        final List<Service> services = buildServices(response);
        return services.size() > 0 ? services.get(0) : null;
    }

    @Override
    public List<Endpoint> searchEndpoint(String keyword, String serviceId, int limit)
        throws IOException {
        final String index = IndexController.LogicIndicesRegister.getPhysicalTableName(
            EndpointTraffic.INDEX_NAME);

        final BoolQueryBuilder query =
            Query.bool()
                 .must(Query.term(EndpointTraffic.SERVICE_ID, serviceId));

        if (!Strings.isNullOrEmpty(keyword)) {
            String matchCName = MatchCNameBuilder.INSTANCE.build(EndpointTraffic.NAME);
            query.must(Query.match(matchCName, keyword));
        }

        final SearchBuilder search = Search.builder().query(query).size(limit);

        final SearchResponse response = getClient().search(index, search.build());

        List<Endpoint> endpoints = new ArrayList<>();
        for (SearchHit searchHit : response.getHits()) {
            Map<String, Object> sourceAsMap = searchHit.getSource();

            final EndpointTraffic endpointTraffic =
                new EndpointTraffic.Builder().storage2Entity(sourceAsMap);

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
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(InstanceTraffic.INDEX_NAME);

        final long minuteTimeBucket = TimeBucket.getMinuteTimeBucket(startTimestamp);
        final BoolQueryBuilder query =
            Query.bool()
                 .must(Query.range(InstanceTraffic.LAST_PING_TIME_BUCKET).gte(minuteTimeBucket))
                 .must(Query.term(InstanceTraffic.SERVICE_ID, serviceId));
        final SearchBuilder search = Search.builder().query(query).size(queryMaxSize);

        final SearchResponse response = getClient().search(index, search.build());

        List<ServiceInstance> serviceInstances = new ArrayList<>();
        for (SearchHit searchHit : response.getHits()) {
            Map<String, Object> sourceAsMap = searchHit.getSource();

            final InstanceTraffic instanceTraffic =
                new InstanceTraffic.Builder().storage2Entity(sourceAsMap);

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
        for (SearchHit hit : response.getHits()) {
            final Map<String, Object> sourceAsMap = hit.getSource();
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
