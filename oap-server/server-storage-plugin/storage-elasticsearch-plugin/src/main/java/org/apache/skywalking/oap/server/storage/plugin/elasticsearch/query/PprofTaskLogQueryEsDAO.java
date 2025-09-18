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

import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Sort;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.profiling.pprof.storage.PprofTaskLogRecord;
import org.apache.skywalking.oap.server.core.query.PprofTaskLog;
import org.apache.skywalking.oap.server.core.query.type.PprofTaskLogOperationType;
import org.apache.skywalking.oap.server.core.storage.profiling.pprof.IPprofTaskLogQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;

public class PprofTaskLogQueryEsDAO extends EsDAO implements IPprofTaskLogQueryDAO {

    private final int queryMaxSize;

    public PprofTaskLogQueryEsDAO(ElasticSearchClient client, int profileTaskQueryMaxSize) {
        super(client);
        // query log size use pprof task query max size * per log count
        this.queryMaxSize = profileTaskQueryMaxSize * 50;
    }

    @Override
    public List<PprofTaskLog> getTaskLogList() throws IOException {
        final String index = IndexController.LogicIndicesRegister.getPhysicalTableName(PprofTaskLogRecord.INDEX_NAME);
        
        final SearchBuilder search = Search.builder().query(Query.bool());
        if (IndexController.LogicIndicesRegister.isMergedTable(PprofTaskLogRecord.INDEX_NAME)) {
            search.query(Query.bool().must(Query.term(IndexController.LogicIndicesRegister.RECORD_TABLE_NAME, PprofTaskLogRecord.INDEX_NAME)));
        }
        
        search.size(queryMaxSize);
        search.sort(PprofTaskLogRecord.OPERATION_TIME, Sort.Order.DESC);

        final SearchResponse response = getClient().search(index, search.build());

        List<PprofTaskLog> tasks = new LinkedList<>();
        for (SearchHit hit : response.getHits().getHits()) {
            tasks.add(parseTaskLog(hit));
        }
        return tasks;
    }

    private PprofTaskLog parseTaskLog(SearchHit data) {
        Map<String, Object> source = data.getSource();
        
        int operationTypeInt = ((Number) source.get(PprofTaskLogRecord.OPERATION_TYPE)).intValue();
        PprofTaskLogOperationType operationType = PprofTaskLogOperationType.parse(operationTypeInt);
        
        return PprofTaskLog.builder()
                .id((String) source.get(PprofTaskLogRecord.TASK_ID))
                .instanceId((String) source.get(PprofTaskLogRecord.INSTANCE_ID))
                .operationType(operationType)
                .operationTime(((Number) source.get(PprofTaskLogRecord.OPERATION_TIME)).longValue())
                .build();
    }
}
