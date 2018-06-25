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

import java.io.IOException;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.UnexpectedException;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceHeartBeatPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.register.*;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.common.xcontent.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class InstanceHeartBeatEsPersistenceDAO extends EsDAO implements IInstanceHeartBeatPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, Instance> {

    private static final Logger logger = LoggerFactory.getLogger(InstanceHeartBeatEsPersistenceDAO.class);

    public InstanceHeartBeatEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @GraphComputingMetric(name = "/persistence/get/" + InstanceTable.TABLE + "/heartbeat")
    @Override public Instance get(String id) {
        GetResponse getResponse = getClient().prepareGet(InstanceTable.TABLE, id).get();
        if (getResponse.isExists()) {
            Map<String, Object> source = getResponse.getSource();

            Instance instance = new Instance();
            instance.setId(id);
            instance.setInstanceId(((Number)source.get(InstanceTable.INSTANCE_ID.getName())).intValue());
            instance.setHeartBeatTime(((Number)source.get(InstanceTable.HEARTBEAT_TIME.getName())).longValue());
            logger.debug("instance id: {} exists", id);
            return instance;
        } else {
            logger.debug("instance id: {} not exists", id);
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(Instance data) {
        throw new UnexpectedException("Received an instance heart beat message under instance id= " + data.getId() + " , which doesn't exist.");
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(Instance data) throws IOException {
        XContentBuilder source = XContentFactory.jsonBuilder().startObject()
            .field(InstanceTable.HEARTBEAT_TIME.getName(), data.getHeartBeatTime())
            .endObject();
        
        return getClient().prepareUpdate(InstanceTable.TABLE, data.getId()).setDoc(source);
    }

    @Override public void deleteHistory(Long timeBucketBefore) {
    }
}
