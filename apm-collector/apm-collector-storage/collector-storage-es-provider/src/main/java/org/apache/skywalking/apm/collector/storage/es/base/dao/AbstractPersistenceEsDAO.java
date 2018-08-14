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

package org.apache.skywalking.apm.collector.storage.es.base.dao;

import java.io.IOException;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.data.StreamData;
import org.apache.skywalking.apm.collector.storage.base.dao.IPersistenceDAO;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractPersistenceEsDAO<STREAM_DATA extends StreamData> extends EsDAO implements IPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, STREAM_DATA> {

    private final Logger logger = LoggerFactory.getLogger(AbstractPersistenceEsDAO.class);

    protected AbstractPersistenceEsDAO(ElasticSearchClient client) {
        super(client);
    }

    protected abstract STREAM_DATA esDataToStreamData(Map<String, Object> source);

    protected abstract String tableName();

    @Override
    public STREAM_DATA get(String id) {
        GetResponse getResponse = getClient().prepareGet(tableName(), id).get();
        if (getResponse.isExists()) {
            STREAM_DATA streamData = esDataToStreamData(getResponse.getSource());
            streamData.setId(id);
            return streamData;
        } else {
            return null;
        }
    }

    protected abstract XContentBuilder esStreamDataToEsData(STREAM_DATA streamData) throws IOException;

    @Override
    public final IndexRequestBuilder prepareBatchInsert(STREAM_DATA streamData) throws IOException {
        XContentBuilder source = esStreamDataToEsData(streamData);
        return getClient().prepareIndex(tableName(), streamData.getId()).setSource(source);
    }

    @Override
    public final UpdateRequestBuilder prepareBatchUpdate(STREAM_DATA streamData) throws IOException {
        XContentBuilder source = esStreamDataToEsData(streamData);
        return getClient().prepareUpdate(tableName(), streamData.getId()).setDoc(source);
    }

    protected abstract String timeBucketColumnNameForDelete();

    @Override
    public final void deleteHistory(Long timeBucketBefore) {
        BulkByScrollResponse response = getClient().prepareDelete(
            QueryBuilders.rangeQuery(timeBucketColumnNameForDelete()).lte(timeBucketBefore),
            tableName())
            .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, tableName());
    }
}
