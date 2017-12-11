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

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.table.segment.Segment;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class SegmentEsPersistenceDAO extends EsDAO implements ISegmentPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, Segment> {

    private final Logger logger = LoggerFactory.getLogger(SegmentEsPersistenceDAO.class);

    public SegmentEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public Segment get(String id) {
        return null;
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(Segment data) {
        return null;
    }

    @Override public IndexRequestBuilder prepareBatchInsert(Segment data) {
        Map<String, Object> source = new HashMap<>();
        source.put(SegmentTable.COLUMN_DATA_BINARY, new String(Base64.getEncoder().encode(data.getDataBinary())));
        source.put(SegmentTable.COLUMN_TIME_BUCKET, data.getTimeBucket());
        logger.debug("segment source: {}", source.toString());
        return getClient().prepareIndex(SegmentTable.TABLE, data.getId()).setSource(source);
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete()
            .filter(QueryBuilders.rangeQuery(SegmentTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket))
            .source(SegmentTable.TABLE)
            .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, SegmentTable.TABLE);
    }
}
