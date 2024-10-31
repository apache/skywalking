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

import com.google.common.collect.Lists;
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.AsyncProfilerTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.JFRProfilingDataRecord;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IJFRDataQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.ElasticSearchConverter;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JFRDataQueryEsDAO extends EsDAO implements IJFRDataQueryDAO {

    public JFRDataQueryEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public List<JFRProfilingDataRecord> getByTaskIdAndInstancesAndEvent(String taskId, List<String> instanceIds, String eventType) {
        if (StringUtil.isBlank(taskId) || StringUtil.isBlank(eventType)) {
            return new ArrayList<>();
        }
        final String index = IndexController.LogicIndicesRegister.getPhysicalTableName(JFRProfilingDataRecord.INDEX_NAME);
        final BoolQueryBuilder query = Query.bool();
        if (IndexController.LogicIndicesRegister.isMergedTable(AsyncProfilerTaskRecord.INDEX_NAME)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.RECORD_TABLE_NAME, JFRProfilingDataRecord.INDEX_NAME));
        }
        query.must(Query.term(JFRProfilingDataRecord.TASK_ID, taskId));
        query.must(Query.term(JFRProfilingDataRecord.EVENT_TYPE, eventType));
        if (CollectionUtils.isNotEmpty(instanceIds)) {
            query.must(Query.terms(JFRProfilingDataRecord.INSTANCE_ID, instanceIds));
        }
        final SearchBuilder search = Search.builder()
                .query(query);

        final SearchResponse response = getClient().search(index, search.build());
        List<JFRProfilingDataRecord> dataRecords = Lists.newArrayList();
        for (SearchHit searchHit : response.getHits().getHits()) {
            dataRecords.add(parseData(searchHit));
        }
        return dataRecords;
    }

    private JFRProfilingDataRecord parseData(SearchHit data) {
        final Map<String, Object> sourceAsMap = data.getSource();
        final JFRProfilingDataRecord.Builder builder = new JFRProfilingDataRecord.Builder();
        return builder.storage2Entity(new ElasticSearchConverter.ToEntity(JFRProfilingDataRecord.INDEX_NAME, sourceAsMap));
    }
}
