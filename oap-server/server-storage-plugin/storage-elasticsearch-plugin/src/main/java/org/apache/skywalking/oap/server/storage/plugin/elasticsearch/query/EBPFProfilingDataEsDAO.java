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
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingDataRecord;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingDataDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchScroller;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchConfig;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.ElasticSearchConverter;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;

import java.util.List;

public class EBPFProfilingDataEsDAO extends EsDAO implements IEBPFProfilingDataDAO {
    private final int scrollingBatchSize;

    public EBPFProfilingDataEsDAO(ElasticSearchClient client, StorageModuleElasticsearchConfig config) {
        super(client);
        this.scrollingBatchSize = config.getProfileDataQueryBatchSize();
    }

    @Override
    public List<EBPFProfilingDataRecord> queryData(List<String> scheduleIdList, long beginTime, long endTime) {
        final String index =
                IndexController.LogicIndicesRegister.getPhysicalTableName(EBPFProfilingDataRecord.INDEX_NAME);
        final BoolQueryBuilder query = Query.bool();
        if (IndexController.LogicIndicesRegister.isMergedTable(EBPFProfilingDataRecord.INDEX_NAME)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.RECORD_TABLE_NAME, EBPFProfilingDataRecord.INDEX_NAME));
        }
        final SearchBuilder search = Search.builder().query(query).size(scrollingBatchSize);
        query.must(Query.terms(EBPFProfilingDataRecord.SCHEDULE_ID, scheduleIdList));
        query.must(Query.range(EBPFProfilingDataRecord.UPLOAD_TIME).gte(beginTime).lt(endTime));

        final var scroller = ElasticSearchScroller
            .<EBPFProfilingDataRecord>builder()
            .client(getClient())
            .search(search.build())
            .index(index)
            .resultConverter(hit -> {
                final var sourceAsMap = hit.getSource();
                final var builder = new EBPFProfilingDataRecord.Builder();
                return builder.storage2Entity(new ElasticSearchConverter.ToEntity(EBPFProfilingDataRecord.INDEX_NAME, sourceAsMap));
            })
            .build();
        return scroller.scroll();
    }
}
