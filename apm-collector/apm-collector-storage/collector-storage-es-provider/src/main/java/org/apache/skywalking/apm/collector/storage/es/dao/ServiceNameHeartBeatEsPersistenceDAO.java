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
import org.apache.skywalking.apm.collector.storage.dao.IServiceNameHeartBeatPersistenceDAO;
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
public class ServiceNameHeartBeatEsPersistenceDAO extends EsDAO implements IServiceNameHeartBeatPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, ServiceName> {

    private static final Logger logger = LoggerFactory.getLogger(ServiceNameHeartBeatEsPersistenceDAO.class);

    public ServiceNameHeartBeatEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @GraphComputingMetric(name = "/persistence/get/" + ServiceNameTable.TABLE + "/heartbeat")
    @Override public ServiceName get(String id) {
        String[] includeSources = {ServiceNameTable.HEARTBEAT_TIME.getName()};
        GetResponse getResponse = getClient().prepareGet(ServiceNameTable.TABLE, id).setFetchSource(includeSources, null).get();
        if (getResponse.isExists()) {
            Map<String, Object> source = getResponse.getSource();

            ServiceName serviceName = new ServiceName();
            serviceName.setId(id);
            serviceName.setServiceId(Integer.valueOf(id));
            serviceName.setHeartBeatTime(((Number)source.get(ServiceNameTable.HEARTBEAT_TIME.getName())).longValue());

            if (logger.isDebugEnabled()) {
                logger.debug("service id: {} is exists", id);
            }
            return serviceName;
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("service id: {} is not exists", id);
            }
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(ServiceName data) {
        throw new UnexpectedException("Received an service name heart beat message under service id= " + data.getId() + " , which doesn't exist.");
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(ServiceName data) throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("service name heart beat, service id: {}, heart beat time: {}", data.getId(), data.getHeartBeatTime());
        }

        XContentBuilder source = XContentFactory.jsonBuilder().startObject()
            .field(ServiceNameTable.HEARTBEAT_TIME.getName(), data.getHeartBeatTime())
            .endObject();

        return getClient().prepareUpdate(ServiceNameTable.TABLE, data.getId()).setDoc(source);
    }

    @Override public void deleteHistory(Long timeBucketBefore) {
    }
}
