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
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingProcessFinderType;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTask;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.EBPFProfilingProcessFinder;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchConfig;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class EBPFProfilingTaskEsDAO extends EsDAO implements IEBPFProfilingTaskDAO {
    private final int taskMaxSize;

    public EBPFProfilingTaskEsDAO(ElasticSearchClient client, StorageModuleElasticsearchConfig config) {
        super(client);
        this.taskMaxSize = config.getProfileTaskQueryMaxSize();
    }

    @Override
    public List<EBPFProfilingTask> queryTasks(EBPFProfilingProcessFinder finder, EBPFProfilingTargetType targetType, long taskStartTime, long latestUpdateTime) throws IOException {
        final String index =
                IndexController.LogicIndicesRegister.getPhysicalTableName(EBPFProfilingTaskRecord.INDEX_NAME);
        final BoolQueryBuilder query = Query.bool();

        if (finder.getFinderType() != null) {
            query.must(Query.term(EBPFProfilingTaskRecord.PROCESS_FIND_TYPE, finder.getFinderType().value()));
        }
        if (finder.getServiceId() != null) {
            query.must(Query.term(EBPFProfilingTaskRecord.SERVICE_ID, finder.getServiceId()));
        }
        if (finder.getInstanceId() != null) {
            query.must(Query.term(EBPFProfilingTaskRecord.INSTANCE_ID, finder.getInstanceId()));
        }
        if (finder.getProcessIdList() != null) {
            query.must(Query.terms(EBPFProfilingTaskRecord.PROCESS_ID, finder.getProcessIdList()));
        }
        if (targetType != null) {
            query.must(Query.term(EBPFProfilingTaskRecord.TARGET_TYPE, targetType.value()));
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

    private EBPFProfilingTask parseTask(final SearchHit hit) {
        final EBPFProfilingTask task = new EBPFProfilingTask();
        task.setTaskId(hit.getId());
        task.setProcessFinderType(EBPFProfilingProcessFinderType.valueOf(((Number) hit.getSource().get(EBPFProfilingTaskRecord.PROCESS_FIND_TYPE)).intValue()));
        final String serviceId = (String) hit.getSource().get(EBPFProfilingTaskRecord.SERVICE_ID);
        task.setServiceId(serviceId);
        task.setServiceName(IDManager.ServiceID.analysisId(serviceId).getName());
        final String instanceId = (String) hit.getSource().get(EBPFProfilingTaskRecord.INSTANCE_ID);
        task.setInstanceId(instanceId);
        task.setServiceName(IDManager.ServiceInstanceID.analysisId(instanceId).getName());
        task.setProcessId((String) hit.getSource().get(EBPFProfilingTaskRecord.PROCESS_ID));
        task.setProcessName((String) hit.getSource().get(EBPFProfilingTaskRecord.PROCESS_NAME));
        task.setTaskStartTime(((Number) hit.getSource().get(EBPFProfilingTaskRecord.START_TIME)).longValue());
        task.setTriggerType(EBPFProfilingTriggerType.valueOf(((Number) hit.getSource().get(EBPFProfilingTaskRecord.TRIGGER_TYPE)).intValue()));
        task.setFixedTriggerDuration(((Number) hit.getSource().get(EBPFProfilingTaskRecord.FIXED_TRIGGER_DURATION)).longValue());
        task.setTargetType(EBPFProfilingTargetType.valueOf(((Number) hit.getSource().get(EBPFProfilingTaskRecord.TARGET_TYPE)).intValue()));
        task.setCreateTime(((Number) hit.getSource().get(EBPFProfilingTaskRecord.CREATE_TIME)).longValue());
        task.setLastUpdateTime(((Number) hit.getSource().get(EBPFProfilingTaskRecord.LAST_UPDATE_TIME)).longValue());

        return task;
    }
}