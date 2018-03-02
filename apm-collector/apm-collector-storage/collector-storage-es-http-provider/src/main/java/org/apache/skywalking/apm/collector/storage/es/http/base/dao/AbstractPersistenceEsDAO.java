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

package org.apache.skywalking.apm.collector.storage.es.http.base.dao;

import java.util.Map;

import org.apache.skywalking.apm.collector.client.elasticsearch.http.ElasticSearchHttpClient;
import org.apache.skywalking.apm.collector.core.data.StreamData;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.base.dao.IPersistenceDAO;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.searchbox.core.DocumentResult;
import io.searchbox.core.Index;
import io.searchbox.core.Update;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractPersistenceEsDAO<STREAM_DATA extends StreamData> extends EsHttpDAO implements IPersistenceDAO<Index, Update, STREAM_DATA> {

    private final Logger logger = LoggerFactory.getLogger(AbstractPersistenceEsDAO.class);

    protected AbstractPersistenceEsDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    protected abstract STREAM_DATA esDataToStreamData(Map<String, Object> source);

    protected abstract String tableName();

    @Override public final STREAM_DATA get(String id) {
        DocumentResult result = getClient().prepareGet(tableName(), id);
        if (result.isSucceeded()) {
            Map<String, Object> map = result.getSourceAsObject(Map.class);
            return esDataToStreamData(map);
        } else {
            return null;
        }
    }

    protected abstract Map<String, Object> esStreamDataToEsData(STREAM_DATA streamData);

    @Override public final Index prepareBatchInsert(STREAM_DATA streamData) {
        Map<String, Object> source = esStreamDataToEsData(streamData);
        return new Index.Builder(source).index(tableName()).type("type").id(streamData.getId()).build();
    }

    @Override public final Update prepareBatchUpdate(STREAM_DATA streamData) {
        Map<String, Object> source = esStreamDataToEsData(streamData);
        return new Update.Builder(source).index(tableName()).id(streamData.getId()).build();
    }

    protected abstract String timeBucketColumnNameForDelete();

    @Override public final void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.rangeQuery(timeBucketColumnNameForDelete()).gte(startTimeBucket).lte(endTimeBucket));

        long deleted = getClient().batchDelete(tableName(), searchSourceBuilder.toString());
        logger.info("Delete {} rows history from {} index.", deleted, tableName());
    }
}
