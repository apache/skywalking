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

package org.apache.skywalking.apm.collector.storage.es.dao;

import java.util.HashMap;
import java.util.Map;

import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.UnexpectedException;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.dao.IGlobalTracePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.global.GlobalTrace;
import org.apache.skywalking.apm.collector.storage.table.global.GlobalTraceTable;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class GlobalTraceEsPersistenceDAO extends EsDAO implements IGlobalTracePersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, GlobalTrace> {

    private final Logger logger = LoggerFactory.getLogger(GlobalTraceEsPersistenceDAO.class);

    public GlobalTraceEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public GlobalTrace get(String id) {
        throw new UnexpectedException("There is no need to merge stream data with database data.");
    }

    @Override
    public UpdateRequestBuilder prepareBatchUpdate(GlobalTrace data) {
        throw new UnexpectedException("There is no need to merge stream data with database data.");
    }

    @Override
    public IndexRequestBuilder prepareBatchInsert(GlobalTrace data) {
        Map<String, Object> target = new HashMap<>();
        target.put(GlobalTraceTable.SEGMENT_ID.getName(), data.getSegmentId());
        target.put(GlobalTraceTable.TRACE_ID.getName(), data.getGlobalTraceId());
        target.put(GlobalTraceTable.TIME_BUCKET.getName(), data.getTimeBucket());
        logger.debug("global trace source: {}", target.toString());
        return getClient().prepareIndex(GlobalTraceTable.TABLE, data.getId()).setSource(target);
    }

    @Override
    public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete(
                QueryBuilders.rangeQuery(GlobalTraceTable.TIME_BUCKET.getName()).gte(startTimeBucket).lte(endTimeBucket),
                GlobalTraceTable.TABLE)
                .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, GlobalTraceTable.TABLE);
    }
}
