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
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingScheduleRecord;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingSchedule;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingScheduleDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchConfig;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class EBPFProfilingScheduleEsDAO extends EsDAO implements IEBPFProfilingScheduleDAO {
    private final int scheduleTaskSize;

    public EBPFProfilingScheduleEsDAO(ElasticSearchClient client, StorageModuleElasticsearchConfig config) {
        super(client);
        this.scheduleTaskSize = config.getProfileTaskQueryMaxSize();
    }

    @Override
    public List<EBPFProfilingSchedule> querySchedules(String taskId) throws IOException {
        final String index =
                IndexController.LogicIndicesRegister.getPhysicalTableName(EBPFProfilingScheduleRecord.INDEX_NAME);
        final BoolQueryBuilder query = Query.bool();
        if (IndexController.LogicIndicesRegister.isMergedTable(EBPFProfilingScheduleRecord.INDEX_NAME)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.METRIC_TABLE_NAME, EBPFProfilingScheduleRecord.INDEX_NAME));
        }
        query.must(Query.term(EBPFProfilingScheduleRecord.TASK_ID, taskId));
        query.must(Query.range(EBPFProfilingScheduleRecord.START_TIME));
        final SearchBuilder search = Search.builder().query(query)
                .sort(EBPFProfilingScheduleRecord.START_TIME, Sort.Order.DESC)
                .size(scheduleTaskSize);

        final SearchResponse response = getClient().search(index, search.build());
        return response.getHits().getHits().stream().map(this::parseSchedule).collect(Collectors.toList());
    }

    private EBPFProfilingSchedule parseSchedule(final SearchHit hit) {
        final EBPFProfilingSchedule schedule = new EBPFProfilingSchedule();
        schedule.setScheduleId((String) hit.getSource().get(EBPFProfilingScheduleRecord.EBPF_PROFILING_SCHEDULE_ID));
        schedule.setTaskId((String) hit.getSource().get(EBPFProfilingScheduleRecord.TASK_ID));
        schedule.setProcessId((String) hit.getSource().get(EBPFProfilingScheduleRecord.PROCESS_ID));
        schedule.setStartTime(((Number) hit.getSource().get(EBPFProfilingScheduleRecord.START_TIME)).longValue());
        schedule.setEndTime(((Number) hit.getSource().get(EBPFProfilingScheduleRecord.END_TIME)).longValue());
        return schedule;
    }
}
