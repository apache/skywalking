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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.skywalking.oap.server.core.query.input.RecordCondition;
import org.apache.skywalking.oap.server.core.query.type.Record;
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Sort;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.query.IRecordsQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;

public class RecordsQueryEsDAO extends EsDAO implements IRecordsQueryDAO {
    public RecordsQueryEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public List<Record> readRecords(final RecordCondition condition,
                                           final String valueColumnName,
                                           final Duration duration) throws IOException {
        final BoolQueryBuilder query =
            Query.bool()
                 .must(Query.range(TopN.TIME_BUCKET)
                            .gte(duration.getStartTimeBucketInSec())
                            .lte(duration.getEndTimeBucketInSec()));
        if (IndexController.LogicIndicesRegister.isMergedTable(condition.getName())) {
            query.must(Query.term(IndexController.LogicIndicesRegister.RECORD_TABLE_NAME, condition.getName()));
        }
        query.must(Query.term(TopN.ENTITY_ID, condition.getParentEntity().buildId()));

        final SearchBuilder search =
            Search.builder()
                  .query(query)
                  .size(condition.getTopN())
                  .sort(
                      valueColumnName,
                      condition.getOrder().equals(Order.DES) ?
                          Sort.Order.DESC : Sort.Order.ASC
                  );
        final SearchResponse response = getClient().search(
            IndexController.LogicIndicesRegister.getPhysicalTableName(condition.getName()),
            search.build()
        );

        List<Record> results = new ArrayList<>(condition.getTopN());

        for (SearchHit searchHit : response.getHits().getHits()) {
            Record record = new Record();
            final Map<String, Object> sourceAsMap = searchHit.getSource();
            record.setName((String) sourceAsMap.get(TopN.STATEMENT));
            final String refId = (String) sourceAsMap.get(TopN.TRACE_ID);
            record.setRefId(StringUtil.isEmpty(refId) ? "" : refId);
            record.setId(record.getRefId());
            record.setValue(sourceAsMap.get(valueColumnName).toString());
            results.add(record);
        }

        return results;
    }
}
