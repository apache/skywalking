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
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.AsyncProfilerTaskLogRecord;
import org.apache.skywalking.oap.server.core.query.AsyncProfilerTaskLog;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTaskLogOperationType;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IAsyncProfilerTaskLogQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class AsyncProfilerTaskLogQueryEsDAO extends EsDAO implements IAsyncProfilerTaskLogQueryDAO {
    private final int queryMaxSize;

    public AsyncProfilerTaskLogQueryEsDAO(ElasticSearchClient client, int queryMaxSize) {
        super(client);
        this.queryMaxSize = queryMaxSize * 50;
    }

    @Override
    public List<AsyncProfilerTaskLog> getTaskLogList() throws IOException {
        final String index = IndexController.LogicIndicesRegister.getPhysicalTableName(
                AsyncProfilerTaskLogRecord.INDEX_NAME);
        final BoolQueryBuilder query = Query.bool();
        if (IndexController.LogicIndicesRegister.isMergedTable(AsyncProfilerTaskLogRecord.INDEX_NAME)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.RECORD_TABLE_NAME, AsyncProfilerTaskLogRecord.INDEX_NAME));
        }
        final SearchBuilder search =
                Search.builder().query(query)
                        .sort(AsyncProfilerTaskLogRecord.OPERATION_TIME, Sort.Order.DESC)
                        .size(queryMaxSize);

        final SearchResponse response = getClient().search(index, search.build());

        final LinkedList<AsyncProfilerTaskLog> tasks = new LinkedList<>();
        for (SearchHit searchHit : response.getHits().getHits()) {
            tasks.add(buildAsyncProfilerTaskLog(searchHit));
        }

        return tasks;
    }

    private AsyncProfilerTaskLog buildAsyncProfilerTaskLog(SearchHit data) {
        Map<String, Object> source = data.getSource();
        int operationTypeInt = ((Number) source.get(AsyncProfilerTaskLogRecord.OPERATION_TYPE)).intValue();
        AsyncProfilerTaskLogOperationType operationType = AsyncProfilerTaskLogOperationType.parse(operationTypeInt);
        return AsyncProfilerTaskLog.builder()
                .id(data.getId())
                .taskId((String) source.get(AsyncProfilerTaskLogRecord.TASK_ID))
                .instanceId((String) source.get(AsyncProfilerTaskLogRecord.INSTANCE_ID))
                .operationType(operationType)
                .operationTime(((Number) source.get(AsyncProfilerTaskLogRecord.OPERATION_TIME)).longValue())
                .build();
    }
}
