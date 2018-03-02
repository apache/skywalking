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

package org.apache.skywalking.apm.collector.storage.es.http.dao;

import java.util.HashMap;
import java.util.Map;

import org.apache.skywalking.apm.collector.client.elasticsearch.http.ElasticSearchHttpClient;
import org.apache.skywalking.apm.collector.core.UnexpectedException;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.dao.IGlobalTracePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.global.GlobalTrace;
import org.apache.skywalking.apm.collector.storage.table.global.GlobalTraceTable;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.searchbox.core.Index;
import io.searchbox.core.Update;

/**
 * @author peng-yongsheng
 */
public class GlobalTraceEsPersistenceDAO extends EsHttpDAO implements IGlobalTracePersistenceDAO<Index, Update, GlobalTrace> {

    private final Logger logger = LoggerFactory.getLogger(GlobalTraceEsPersistenceDAO.class);

    public GlobalTraceEsPersistenceDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public GlobalTrace get(String id) {
        throw new UnexpectedException("There is no need to merge stream data with database data.");
    }

    @Override public Update prepareBatchUpdate(GlobalTrace data) {
        throw new UnexpectedException("There is no need to merge stream data with database data.");
    }

    @Override public Index prepareBatchInsert(GlobalTrace data) {
        Map<String, Object> source = new HashMap<>();
        source.put(GlobalTraceTable.COLUMN_SEGMENT_ID, data.getSegmentId());
        source.put(GlobalTraceTable.COLUMN_GLOBAL_TRACE_ID, data.getGlobalTraceId());
        source.put(GlobalTraceTable.COLUMN_TIME_BUCKET, data.getTimeBucket());
        logger.debug("global trace source: {}", source.toString());
        return new Index.Builder(source).index(GlobalTraceTable.TABLE).type(GlobalTraceTable.TABLE_TYPE).id(data.getId()).build();
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
//        BulkByScrollResponse response = getClient().prepareDelete()
//            .filter(QueryBuilders.rangeQuery(GlobalTraceTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket))
//            .source(GlobalTraceTable.TABLE)
//            .get();
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.rangeQuery(GlobalTraceTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket));

        long deleted =  getClient().batchDelete(GlobalTraceTable.TABLE,searchSourceBuilder.toString());
        logger.info("Delete {} rows history from {} index.", deleted, GlobalTraceTable.TABLE);
    }
}
