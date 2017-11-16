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
import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.storage.dao.INodeReferencePersistenceDAO;
import org.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.skywalking.apm.collector.storage.table.noderef.NodeReference;
import org.skywalking.apm.collector.storage.table.noderef.NodeReferenceTable;

/**
 * @author peng-yongsheng
 */
public class NodeReferenceEsPersistenceDAO extends EsDAO implements INodeReferencePersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, NodeReference> {

    public NodeReferenceEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public NodeReference get(String id) {
        GetResponse getResponse = getClient().prepareGet(NodeReferenceTable.TABLE, id).get();
        if (getResponse.isExists()) {
            NodeReference nodeReference = new NodeReference(id);
            Map<String, Object> source = getResponse.getSource();
            nodeReference.setFrontApplicationId(((Number)source.get(NodeReferenceTable.COLUMN_FRONT_APPLICATION_ID)).intValue());
            nodeReference.setBehindApplicationId(((Number)source.get(NodeReferenceTable.COLUMN_BEHIND_APPLICATION_ID)).intValue());
            nodeReference.setS1Lte(((Number)source.get(NodeReferenceTable.COLUMN_S1_LTE)).intValue());
            nodeReference.setS3Lte(((Number)source.get(NodeReferenceTable.COLUMN_S3_LTE)).intValue());
            nodeReference.setS5Lte(((Number)source.get(NodeReferenceTable.COLUMN_S5_LTE)).intValue());
            nodeReference.setS5Gt(((Number)source.get(NodeReferenceTable.COLUMN_S5_GT)).intValue());
            nodeReference.setSummary(((Number)source.get(NodeReferenceTable.COLUMN_SUMMARY)).intValue());
            nodeReference.setError(((Number)source.get(NodeReferenceTable.COLUMN_ERROR)).intValue());
            nodeReference.setTimeBucket(((Number)source.get(NodeReferenceTable.COLUMN_TIME_BUCKET)).longValue());
            return nodeReference;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(NodeReference data) {
        Map<String, Object> source = new HashMap<>();
        source.put(NodeReferenceTable.COLUMN_FRONT_APPLICATION_ID, data.getFrontApplicationId());
        source.put(NodeReferenceTable.COLUMN_BEHIND_APPLICATION_ID, data.getBehindApplicationId());
        source.put(NodeReferenceTable.COLUMN_S1_LTE, data.getS1Lte());
        source.put(NodeReferenceTable.COLUMN_S3_LTE, data.getS3Lte());
        source.put(NodeReferenceTable.COLUMN_S5_LTE, data.getS5Lte());
        source.put(NodeReferenceTable.COLUMN_S5_GT, data.getS5Gt());
        source.put(NodeReferenceTable.COLUMN_SUMMARY, data.getSummary());
        source.put(NodeReferenceTable.COLUMN_ERROR, data.getError());
        source.put(NodeReferenceTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareIndex(NodeReferenceTable.TABLE, data.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(NodeReference data) {
        Map<String, Object> source = new HashMap<>();
        source.put(NodeReferenceTable.COLUMN_FRONT_APPLICATION_ID, data.getFrontApplicationId());
        source.put(NodeReferenceTable.COLUMN_BEHIND_APPLICATION_ID, data.getBehindApplicationId());
        source.put(NodeReferenceTable.COLUMN_S1_LTE, data.getS1Lte());
        source.put(NodeReferenceTable.COLUMN_S3_LTE, data.getS3Lte());
        source.put(NodeReferenceTable.COLUMN_S5_LTE, data.getS5Lte());
        source.put(NodeReferenceTable.COLUMN_S5_GT, data.getS5Gt());
        source.put(NodeReferenceTable.COLUMN_SUMMARY, data.getSummary());
        source.put(NodeReferenceTable.COLUMN_ERROR, data.getError());
        source.put(NodeReferenceTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareUpdate(NodeReferenceTable.TABLE, data.getId()).setDoc(source);
    }
}
