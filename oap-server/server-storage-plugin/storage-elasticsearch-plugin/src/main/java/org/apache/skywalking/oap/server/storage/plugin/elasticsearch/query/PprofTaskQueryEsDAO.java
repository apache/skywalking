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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Sort;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.profiling.pprof.storage.PprofTaskRecord;
import org.apache.skywalking.oap.server.core.query.type.PprofTask;
import org.apache.skywalking.oap.server.core.query.type.PprofEventType;
import org.apache.skywalking.oap.server.core.storage.profiling.pprof.IPprofTaskQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;

public class PprofTaskQueryEsDAO extends EsDAO implements IPprofTaskQueryDAO {
    private static final Gson GSON = new Gson();

    private final int queryMaxSize;

    public PprofTaskQueryEsDAO(ElasticSearchClient client, int queryMaxSize) {
        super(client);
        this.queryMaxSize = queryMaxSize;
    }

    @Override
    public List<PprofTask> getTaskList(String serviceId, Long startTimeBucket, Long endTimeBucket, Integer limit) throws IOException {
        final String index = IndexController.LogicIndicesRegister.getPhysicalTableName(PprofTaskRecord.INDEX_NAME);
        final BoolQueryBuilder query = Query.bool();
        if (IndexController.LogicIndicesRegister.isMergedTable(PprofTaskRecord.INDEX_NAME)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.RECORD_TABLE_NAME, PprofTaskRecord.INDEX_NAME));
        }

        if (StringUtil.isNotEmpty(serviceId)) {
            query.must(Query.term(PprofTaskRecord.SERVICE_ID, serviceId));
        }

        if (startTimeBucket != null) {
            query.must(Query.range(PprofTaskRecord.TIME_BUCKET).gte(startTimeBucket));
        }

        if (endTimeBucket != null) {
            query.must(Query.range(PprofTaskRecord.TIME_BUCKET).lte(endTimeBucket));
        }

        final SearchBuilder search = Search.builder().query(query);

        if (limit != null) {
            search.size(limit);
        } else {
            search.size(queryMaxSize);
        }

        search.sort(PprofTaskRecord.CREATE_TIME, Sort.Order.DESC);

        final SearchResponse response = getClient().search(index, search.build());

        List<PprofTask> tasks = new LinkedList<>();
        for (SearchHit hit : response.getHits().getHits()) {
            tasks.add(parseTask(hit));
        }
        return tasks;
    }

    @Override
    public PprofTask getById(String id) throws IOException {
        if (StringUtil.isEmpty(id)) {
            return null;
        }
        final String index = IndexController.LogicIndicesRegister.getPhysicalTableName(PprofTaskRecord.INDEX_NAME);
        final BoolQueryBuilder query = Query.bool();
        if (IndexController.LogicIndicesRegister.isMergedTable(PprofTaskRecord.INDEX_NAME)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.RECORD_TABLE_NAME, PprofTaskRecord.INDEX_NAME));
        }
        query.must(Query.term(PprofTaskRecord.TASK_ID, id));

        final SearchBuilder search = Search.builder().query(query).size(1);
        final SearchResponse response = getClient().search(index, search.build());

        if (!response.getHits().getHits().isEmpty()) {
            return parseTask(response.getHits().getHits().iterator().next());
        }
        return null;
    }

    private PprofTask parseTask(SearchHit data) {
        Map<String, Object> source = data.getSource();
        Type listType = new TypeToken<List<String>>() {
        }.getType();

        String serviceInstanceIds = (String) source.get(PprofTaskRecord.SERVICE_INSTANCE_IDS);

        List<String> instanceIdList = GSON.fromJson(serviceInstanceIds, listType);
        
        // Convert string events to PprofEventType enum
        String eventsStr = (String) source.get(PprofTaskRecord.EVENT_TYPES);
        PprofEventType eventType = null;
        if (StringUtil.isNotEmpty(eventsStr)) {
            try {
                eventType = PprofEventType.valueOfString(eventsStr);
            } catch (Exception e) {
                // Default to CPU if conversion fails
                eventType = PprofEventType.CPU;
            }
        }
        
        return PprofTask.builder()
                .id((String) source.get(PprofTaskRecord.TASK_ID))
                .serviceId((String) source.get(PprofTaskRecord.SERVICE_ID))
                .serviceInstanceIds(instanceIdList)
                .createTime(((Number) source.get(PprofTaskRecord.CREATE_TIME)).longValue())
                .startTime(((Number) source.get(PprofTaskRecord.START_TIME)).longValue())
                .events(eventType)
                .duration(((Number) source.get(PprofTaskRecord.DURATION)).intValue())
                .dumpPeriod(((Number) source.get(PprofTaskRecord.DUMP_PERIOD)).intValue())
                .build();
    }
}
