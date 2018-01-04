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
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceMappingPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMapping;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMappingTable;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstanceMappingEsPersistenceDAO extends EsDAO implements IInstanceMappingPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, InstanceMapping> {

    private final Logger logger = LoggerFactory.getLogger(InstanceMappingEsPersistenceDAO.class);

    public InstanceMappingEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public InstanceMapping get(String id) {
        GetResponse getResponse = getClient().prepareGet(InstanceMappingTable.TABLE, id).get();
        if (getResponse.isExists()) {
            InstanceMapping instanceMapping = new InstanceMapping(id);
            Map<String, Object> source = getResponse.getSource();
            instanceMapping.setApplicationId(((Number)source.get(InstanceMappingTable.COLUMN_APPLICATION_ID)).intValue());
            instanceMapping.setInstanceId(((Number)source.get(InstanceMappingTable.COLUMN_INSTANCE_ID)).intValue());
            instanceMapping.setAddressId(((Number)source.get(InstanceMappingTable.COLUMN_ADDRESS_ID)).intValue());
            instanceMapping.setTimeBucket(((Number)source.get(InstanceMappingTable.COLUMN_TIME_BUCKET)).longValue());
            return instanceMapping;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(InstanceMapping data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceMappingTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(InstanceMappingTable.COLUMN_INSTANCE_ID, data.getInstanceId());
        source.put(InstanceMappingTable.COLUMN_ADDRESS_ID, data.getAddressId());
        source.put(InstanceMappingTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareIndex(InstanceMappingTable.TABLE, data.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(InstanceMapping data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceMappingTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(InstanceMappingTable.COLUMN_INSTANCE_ID, data.getInstanceId());
        source.put(InstanceMappingTable.COLUMN_ADDRESS_ID, data.getAddressId());
        source.put(InstanceMappingTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareUpdate(InstanceMappingTable.TABLE, data.getId()).setDoc(source);
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete()
            .filter(QueryBuilders.rangeQuery(InstanceMappingTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket))
            .source(InstanceMappingTable.TABLE)
            .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, InstanceMappingTable.TABLE);
    }
}
