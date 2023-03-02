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

import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Sort;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchConfig;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.ElasticSearchConverter;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EBPFProfilingTaskEsDAO extends EsDAO implements IEBPFProfilingTaskDAO {
    private final int taskMaxSize;

    public EBPFProfilingTaskEsDAO(ElasticSearchClient client, StorageModuleElasticsearchConfig config) {
        super(client);
        this.taskMaxSize = config.getProfileTaskQueryMaxSize();
    }

    @Override
    public List<EBPFProfilingTaskRecord> queryTasksByServices(List<String> serviceIdList, EBPFProfilingTriggerType triggerType, long taskStartTime, long latestUpdateTime) throws IOException {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(EBPFProfilingTaskRecord.INDEX_NAME);
        final BoolQueryBuilder query = Query.bool();
        if (IndexController.LogicIndicesRegister.isMergedTable(EBPFProfilingTaskRecord.INDEX_NAME)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.RECORD_TABLE_NAME, EBPFProfilingTaskRecord.INDEX_NAME));
        }

        if (CollectionUtils.isNotEmpty(serviceIdList)) {
            query.must(Query.terms(EBPFProfilingTaskRecord.SERVICE_ID, serviceIdList));
        }
        if (taskStartTime > 0) {
            query.must(Query.range(EBPFProfilingTaskRecord.START_TIME).gte(taskStartTime));
        }
        if (latestUpdateTime > 0) {
            query.must(Query.range(EBPFProfilingTaskRecord.LAST_UPDATE_TIME).gt(latestUpdateTime));
        }
        if (triggerType != null) {
            query.must(Query.term(EBPFProfilingTaskRecord.TRIGGER_TYPE, triggerType));
        }

        final SearchBuilder search = Search.builder().query(query)
            .sort(EBPFProfilingTaskRecord.CREATE_TIME, Sort.Order.DESC)
            .size(taskMaxSize);

        final SearchResponse response = getClient().search(index, search.build());
        return response.getHits().getHits().stream().map(this::parseTask).collect(Collectors.toList());
    }

    @Override
    public List<EBPFProfilingTaskRecord> queryTasksByTargets(String serviceId, String serviceInstanceId, List<EBPFProfilingTargetType> targetTypes,
                                                             EBPFProfilingTriggerType triggerType, long taskStartTime, long latestUpdateTime) throws IOException {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(EBPFProfilingTaskRecord.INDEX_NAME);
        final BoolQueryBuilder query = Query.bool();

        if (StringUtil.isNotEmpty(serviceId)) {
            query.must(Query.term(EBPFProfilingTaskRecord.SERVICE_ID, serviceId));
        }
        if (StringUtil.isNotEmpty(serviceInstanceId)) {
            query.must(Query.term(EBPFProfilingTaskRecord.INSTANCE_ID, serviceInstanceId));
        }
        if (CollectionUtils.isNotEmpty(targetTypes)) {
            query.must(Query.terms(EBPFProfilingTaskRecord.TARGET_TYPE, targetTypes.stream()
                .map(EBPFProfilingTargetType::value).collect(Collectors.toList())));
        }
        if (triggerType != null) {
            query.must(Query.term(EBPFProfilingTaskRecord.TRIGGER_TYPE, triggerType.value()));
        }
        if (taskStartTime > 0) {
            query.must(Query.range(EBPFProfilingTaskRecord.START_TIME).gte(taskStartTime));
        }
        if (latestUpdateTime > 0) {
            query.must(Query.range(EBPFProfilingTaskRecord.LAST_UPDATE_TIME).gt(latestUpdateTime));
        }

        final SearchBuilder search = Search.builder().query(query)
            .sort(EBPFProfilingTaskRecord.CREATE_TIME, Sort.Order.DESC)
            .size(taskMaxSize);

        final SearchResponse response = getClient().search(index, search.build());
        return response.getHits().getHits().stream().map(this::parseTask).collect(Collectors.toList());
    }

    @Override
    public List<EBPFProfilingTaskRecord> queryByLogicalId(String id) throws IOException {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(EBPFProfilingTaskRecord.INDEX_NAME);
        final BoolQueryBuilder query = Query.bool().must(Query.term(EBPFProfilingTaskRecord.LOGICAL_ID, id));

        final SearchBuilder search = Search.builder().query(query).size(taskMaxSize);

        final SearchResponse response = getClient().search(index, search.build());
        return response.getHits().getHits().stream().map(this::parseTask).collect(Collectors.toList());
    }

    private EBPFProfilingTaskRecord parseTask(final SearchHit hit) {
        final Map<String, Object> sourceAsMap = hit.getSource();
        final EBPFProfilingTaskRecord.Builder builder = new EBPFProfilingTaskRecord.Builder();
        return builder.storage2Entity(new ElasticSearchConverter.ToEntity(EBPFProfilingTaskRecord.INDEX_NAME, sourceAsMap));
    }
}
