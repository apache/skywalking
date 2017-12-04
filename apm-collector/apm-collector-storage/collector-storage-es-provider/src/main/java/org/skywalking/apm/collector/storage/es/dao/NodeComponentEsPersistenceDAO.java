/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.storage.es.dao;

import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.dao.INodeComponentPersistenceDAO;
import org.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.skywalking.apm.collector.storage.table.node.NodeComponent;
import org.skywalking.apm.collector.storage.table.node.NodeComponentTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class NodeComponentEsPersistenceDAO extends EsDAO implements INodeComponentPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, NodeComponent> {

    private final Logger logger = LoggerFactory.getLogger(NodeComponentEsPersistenceDAO.class);

    public NodeComponentEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public NodeComponent get(String id) {
        GetResponse getResponse = getClient().prepareGet(NodeComponentTable.TABLE, id).get();
        if (getResponse.isExists()) {
            NodeComponent nodeComponent = new NodeComponent(id);
            Map<String, Object> source = getResponse.getSource();
            nodeComponent.setComponentId(((Number)source.get(NodeComponentTable.COLUMN_COMPONENT_ID)).intValue());
            nodeComponent.setPeerId(((Number)source.get(NodeComponentTable.COLUMN_PEER_ID)).intValue());
            nodeComponent.setTimeBucket((Long)source.get(NodeComponentTable.COLUMN_TIME_BUCKET));
            return nodeComponent;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(NodeComponent data) {
        Map<String, Object> source = new HashMap<>();
        source.put(NodeComponentTable.COLUMN_COMPONENT_ID, data.getComponentId());
        source.put(NodeComponentTable.COLUMN_PEER_ID, data.getPeerId());
        source.put(NodeComponentTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareIndex(NodeComponentTable.TABLE, data.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(NodeComponent data) {
        Map<String, Object> source = new HashMap<>();
        source.put(NodeComponentTable.COLUMN_COMPONENT_ID, data.getComponentId());
        source.put(NodeComponentTable.COLUMN_PEER_ID, data.getPeerId());
        source.put(NodeComponentTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareUpdate(NodeComponentTable.TABLE, data.getId()).setDoc(source);
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete()
            .filter(QueryBuilders.rangeQuery(NodeComponentTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket))
            .source(NodeComponentTable.TABLE)
            .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, NodeComponentTable.TABLE);
    }
}
