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

package org.apache.skywalking.apm.collector.storage.es.dao.register;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.storage.dao.register.IServiceNameRegisterDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceName;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceNameTable;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ServiceNameRegisterEsDAO extends EsDAO implements IServiceNameRegisterDAO {

    private final Logger logger = LoggerFactory.getLogger(ServiceNameRegisterEsDAO.class);

    public ServiceNameRegisterEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public int getMaxServiceId() {
        return getMaxId(ServiceNameTable.TABLE, ServiceNameTable.SERVICE_ID.getName());
    }

    @Override public int getMinServiceId() {
        return getMinId(ServiceNameTable.TABLE, ServiceNameTable.SERVICE_ID.getName());
    }

    @Override public void save(ServiceName serviceName) {
        logger.debug("save service name register info, application getApplicationId: {}, service name: {}", serviceName.getId(), serviceName.getServiceName());
        ElasticSearchClient client = getClient();
        Map<String, Object> target = new HashMap<>();
        target.put(ServiceNameTable.SERVICE_ID.getName(), serviceName.getServiceId());
        target.put(ServiceNameTable.APPLICATION_ID.getName(), serviceName.getApplicationId());
        target.put(ServiceNameTable.SERVICE_NAME.getName(), serviceName.getServiceName());
        target.put(ServiceNameTable.SERVICE_NAME_KEYWORD.getName(), serviceName.getServiceName());
        target.put(ServiceNameTable.SRC_SPAN_TYPE.getName(), serviceName.getSrcSpanType());

        IndexResponse response = client.prepareIndex(ServiceNameTable.TABLE, serviceName.getId()).setSource(target).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE).get();
        logger.debug("save service name register info, application getApplicationId: {}, service name: {}, status: {}", serviceName.getId(), serviceName.getServiceName(), response.status().name());
    }
}
