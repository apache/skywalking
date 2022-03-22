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
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchParams;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessDetectType;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.query.enumeration.Language;
import org.apache.skywalking.oap.server.core.query.type.Attribute;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.Process;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchConfig;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.MatchCNameBuilder;

import static org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic.PropertyUtil.LANGUAGE;

public class MetadataQueryEsDAO extends EsDAO implements IMetadataQueryDAO {
    private final int queryMaxSize;
    private final int scrollingBatchSize;

    public MetadataQueryEsDAO(
        ElasticSearchClient client,
        StorageModuleElasticsearchConfig config) {
        super(client);
        this.queryMaxSize = config.getMetadataQueryMaxSize();
        this.scrollingBatchSize = config.getScrollingBatchSize();
    }

    @Override
    public List<Service> listServices(final String layer, final String group) throws IOException {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(ServiceTraffic.INDEX_NAME);

        final int batchSize = Math.min(queryMaxSize, scrollingBatchSize);
        final BoolQueryBuilder query =
            Query.bool();
        final SearchBuilder search = Search.builder().query(query).size(batchSize);
        if (StringUtil.isNotEmpty(layer)) {
            query.must(Query.term(ServiceTraffic.LAYER, Layer.valueOf(layer).value()));
        }
        if (StringUtil.isNotEmpty(group)) {
            query.must(Query.term(ServiceTraffic.GROUP, group));
        }
        final SearchParams params = new SearchParams().scroll(SCROLL_CONTEXT_RETENTION);
        final List<Service> services = new ArrayList<>();

        SearchResponse results = getClient().search(index, search.build(), params);
        while (results.getHits().getTotal() > 0) {
            final List<Service> batch = buildServices(results);
            services.addAll(batch);
            // The last iterate, there is no more data
            if (batch.size() < batchSize) {
                break;
            }
            // We've got enough data
            if (services.size() >= queryMaxSize) {
                break;
            }
            results = getClient().scroll(SCROLL_CONTEXT_RETENTION, results.getScrollId());
        }
        return services;
    }

    @Override
    public List<Service> getServices(final String serviceId) throws IOException {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(ServiceTraffic.INDEX_NAME);
        final BoolQueryBuilder query =
            Query.bool()
                 .must(Query.term(ServiceTraffic.SERVICE_ID, serviceId));
        final SearchBuilder search = Search.builder().query(query).size(queryMaxSize);

        final SearchResponse response = getClient().search(index, search.build());
        return buildServices(response);
    }

    @Override
    public List<ServiceInstance> listInstances(long startTimestamp, long endTimestamp,
                                               String serviceId) throws IOException {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(InstanceTraffic.INDEX_NAME);

        final long minuteTimeBucket = TimeBucket.getMinuteTimeBucket(startTimestamp);
        final BoolQueryBuilder query =
            Query.bool()
                 .must(Query.range(InstanceTraffic.LAST_PING_TIME_BUCKET).gte(minuteTimeBucket))
                 .must(Query.term(InstanceTraffic.SERVICE_ID, serviceId));
        final int batchSize = Math.min(queryMaxSize, scrollingBatchSize);
        final SearchBuilder search = Search.builder().query(query).size(batchSize);

        final List<ServiceInstance> instances = new ArrayList<>();
        SearchResponse response = getClient().search(index, search.build());
        while (response.getHits().getTotal() > 0) {
            final List<ServiceInstance> batch = buildInstances(response);
            instances.addAll(batch);
            if (batch.size() < batchSize) {
                break;
            }
            if (batch.size() >= queryMaxSize) {
                break;
            }
            response = getClient().scroll(SCROLL_CONTEXT_RETENTION, response.getScrollId());
        }
        return instances;
    }

    @Override
    public ServiceInstance getInstance(final String instanceId) throws IOException {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(InstanceTraffic.INDEX_NAME);
        final BoolQueryBuilder query =
            Query.bool()
                 .must(Query.term("_id", instanceId));
        final SearchBuilder search = Search.builder().query(query).size(1);

        final SearchResponse response = getClient().search(index, search.build());
        final List<ServiceInstance> instances = buildInstances(response);
        return instances.size() > 0 ? instances.get(0) : null;
    }

    @Override
    public List<Endpoint> findEndpoint(String keyword, String serviceId, int limit)
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
                new EndpointTraffic.Builder().storage2Entity(new HashMapConverter.ToEntity(sourceAsMap));

            Endpoint endpoint = new Endpoint();
            endpoint.setId(endpointTraffic.id());
            endpoint.setName((String) sourceAsMap.get(EndpointTraffic.NAME));
            endpoints.add(endpoint);
        }

        return endpoints;
    }

    @Override
    public List<Process> listProcesses(String serviceId, String instanceId, String agentId) throws IOException {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(ProcessTraffic.INDEX_NAME);

        final BoolQueryBuilder query = Query.bool();
        final SearchBuilder search = Search.builder().query(query).size(queryMaxSize);
        if (StringUtil.isNotEmpty(serviceId)) {
            query.must(Query.term(ProcessTraffic.SERVICE_ID, serviceId));
        }
        if (StringUtil.isNotEmpty(instanceId)) {
            query.must(Query.term(ProcessTraffic.INSTANCE_ID, instanceId));
        }
        if (StringUtil.isNotEmpty(agentId)) {
            query.must(Query.term(ProcessTraffic.AGENT_ID, agentId));
        }
        final SearchResponse results = getClient().search(index, search.build());

        return buildProcesses(results);
    }

    @Override
    public Process getProcess(String processId) throws IOException {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(ProcessTraffic.INDEX_NAME);
        final BoolQueryBuilder query = Query.bool()
                                            .must(Query.term("_id", processId));
        final SearchBuilder search = Search.builder().query(query).size(queryMaxSize);

        final SearchResponse response = getClient().search(index, search.build());
        final List<Process> processes = buildProcesses(response);
        return processes.isEmpty() ? null : processes.get(0);
    }

    private List<Service> buildServices(SearchResponse response) {
        List<Service> services = new ArrayList<>();
        for (SearchHit hit : response.getHits()) {
            final Map<String, Object> sourceAsMap = hit.getSource();
            final ServiceTraffic.Builder builder = new ServiceTraffic.Builder();
            final ServiceTraffic serviceTraffic = builder.storage2Entity(new HashMapConverter.ToEntity(sourceAsMap));
            String serviceName = serviceTraffic.getName();
            Service service = new Service();
            service.setId(serviceTraffic.getServiceId());
            service.setName(serviceName);
            service.setShortName(serviceTraffic.getShortName());
            service.setGroup(serviceTraffic.getGroup());
            service.getLayers().add(serviceTraffic.getLayer().name());
            services.add(service);
        }
        return services;
    }

    private List<ServiceInstance> buildInstances(SearchResponse response) {
        List<ServiceInstance> serviceInstances = new ArrayList<>();
        for (SearchHit searchHit : response.getHits()) {
            Map<String, Object> sourceAsMap = searchHit.getSource();

            final InstanceTraffic instanceTraffic =
                new InstanceTraffic.Builder().storage2Entity(new HashMapConverter.ToEntity(sourceAsMap));

            ServiceInstance serviceInstance = new ServiceInstance();
            serviceInstance.setId(instanceTraffic.id());
            serviceInstance.setName(instanceTraffic.getName());
            serviceInstance.setInstanceUUID(serviceInstance.getId());
            serviceInstance.setLayer(instanceTraffic.getLayer().name());

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

    private List<Process> buildProcesses(SearchResponse response) {
        List<Process> processes = new ArrayList<>();
        for (SearchHit searchHit : response.getHits()) {
            Map<String, Object> sourceAsMap = searchHit.getSource();

            final ProcessTraffic processTraffic =
                new ProcessTraffic.Builder().storage2Entity(new HashMapConverter.ToEntity(sourceAsMap));

            Process process = new Process();
            process.setId(processTraffic.id());
            process.setName(processTraffic.getName());
            final String serviceId = processTraffic.getServiceId();
            process.setServiceId(serviceId);
            process.setServiceName(IDManager.ServiceID.analysisId(serviceId).getName());
            final String instanceId = processTraffic.getInstanceId();
            process.setInstanceId(instanceId);
            process.setInstanceName(IDManager.ServiceInstanceID.analysisId(instanceId).getName());
            process.setLayer(Layer.valueOf(processTraffic.getLayer()).name());
            process.setAgentId(processTraffic.getAgentId());
            process.setDetectType(ProcessDetectType.valueOf(processTraffic.getDetectType()).name());

            JsonObject properties = processTraffic.getProperties();
            if (properties != null) {
                for (Map.Entry<String, JsonElement> property : properties.entrySet()) {
                    String key = property.getKey();
                    String value = property.getValue().getAsString();
                    process.getAttributes().add(new Attribute(key, value));
                }
            }
            processes.add(process);
        }
        return processes;
    }
}
