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
import org.skywalking.apm.collector.storage.dao.INodeMappingPersistenceDAO;
import org.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.skywalking.apm.collector.storage.table.node.NodeMapping;
import org.skywalking.apm.collector.storage.table.node.NodeMappingTable;

/**
 * @author peng-yongsheng
 */
public class NodeMappingEsPersistenceDAO extends EsDAO implements INodeMappingPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, NodeMapping> {

    @Override public NodeMapping get(String id) {
        GetResponse getResponse = getClient().prepareGet(NodeMappingTable.TABLE, id).get();
        if (getResponse.isExists()) {
            NodeMapping nodeMapping = new NodeMapping(id);
            Map<String, Object> source = getResponse.getSource();
            nodeMapping.setApplicationId(((Number)source.get(NodeMappingTable.COLUMN_APPLICATION_ID)).intValue());
            nodeMapping.setAddressId(((Number)source.get(NodeMappingTable.COLUMN_ADDRESS_ID)).intValue());
            nodeMapping.setAddress((String)source.get(NodeMappingTable.COLUMN_ADDRESS));
            nodeMapping.setTimeBucket(((Number)source.get(NodeMappingTable.COLUMN_TIME_BUCKET)).longValue());
            return nodeMapping;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(NodeMapping data) {
        Map<String, Object> source = new HashMap<>();
        source.put(NodeMappingTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(NodeMappingTable.COLUMN_ADDRESS_ID, data.getAddressId());
        source.put(NodeMappingTable.COLUMN_ADDRESS, data.getAddress());
        source.put(NodeMappingTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareIndex(NodeMappingTable.TABLE, data.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(NodeMapping data) {
        Map<String, Object> source = new HashMap<>();
        source.put(NodeMappingTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(NodeMappingTable.COLUMN_ADDRESS_ID, data.getAddressId());
        source.put(NodeMappingTable.COLUMN_ADDRESS, data.getAddress());
        source.put(NodeMappingTable.COLUMN_TIME_BUCKET, data.getTimeBucket());
        return getClient().prepareUpdate(NodeMappingTable.TABLE, data.getId()).setDoc(source);
    }
}
