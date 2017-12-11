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
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationComponentPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationComponent;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationComponentTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ApplicationComponentEsPersistenceDAO extends EsDAO implements IApplicationComponentPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, ApplicationComponent> {

    private final Logger logger = LoggerFactory.getLogger(ApplicationComponentEsPersistenceDAO.class);

    public ApplicationComponentEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public ApplicationComponent get(String id) {
        GetResponse getResponse = getClient().prepareGet(ApplicationComponentTable.TABLE, id).get();
        if (getResponse.isExists()) {
            ApplicationComponent applicationComponent = new ApplicationComponent(id);
            Map<String, Object> source = getResponse.getSource();
            applicationComponent.setComponentId(((Number)source.get(ApplicationComponentTable.COLUMN_COMPONENT_ID)).intValue());
            applicationComponent.setPeerId(((Number)source.get(ApplicationComponentTable.COLUMN_PEER_ID)).intValue());
            applicationComponent.setTimeBucket((Long)source.get(ApplicationComponentTable.COLUMN_TIME_BUCKET));
            return applicationComponent;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(ApplicationComponent data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationComponentTable.COLUMN_COMPONENT_ID, data.getComponentId());
        source.put(ApplicationComponentTable.COLUMN_PEER_ID, data.getPeerId());
        source.put(ApplicationComponentTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareIndex(ApplicationComponentTable.TABLE, data.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(ApplicationComponent data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationComponentTable.COLUMN_COMPONENT_ID, data.getComponentId());
        source.put(ApplicationComponentTable.COLUMN_PEER_ID, data.getPeerId());
        source.put(ApplicationComponentTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareUpdate(ApplicationComponentTable.TABLE, data.getId()).setDoc(source);
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete()
            .filter(QueryBuilders.rangeQuery(ApplicationComponentTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket))
            .source(ApplicationComponentTable.TABLE)
            .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, ApplicationComponentTable.TABLE);
    }
}
