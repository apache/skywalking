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

package org.apache.skywalking.apm.collector.storage.es.http.dao.register;

import java.util.HashMap;
import java.util.Map;

import org.apache.skywalking.apm.collector.client.elasticsearch.http.ElasticSearchHttpClient;
import org.apache.skywalking.apm.collector.storage.dao.register.IServiceNameRegisterDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceName;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceNameTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author cyberdak
 */
public class ServiceNameRegisterEsDAO extends EsHttpDAO implements IServiceNameRegisterDAO {

    private final Logger logger = LoggerFactory.getLogger(ServiceNameRegisterEsDAO.class);

    public ServiceNameRegisterEsDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public int getMaxServiceId() {
        return getMaxId(ServiceNameTable.TABLE, ServiceNameTable.COLUMN_SERVICE_ID);
    }

    @Override public int getMinServiceId() {
        return getMinId(ServiceNameTable.TABLE, ServiceNameTable.COLUMN_SERVICE_ID);
    }

    @Override public void save(ServiceName serviceName) {
        logger.debug("save service name register info, application getId: {}, service name: {}", serviceName.getId(), serviceName.getServiceName());
        ElasticSearchHttpClient client = getClient();
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceNameTable.COLUMN_SERVICE_ID, serviceName.getServiceId());
        source.put(ServiceNameTable.COLUMN_APPLICATION_ID, serviceName.getApplicationId());
        source.put(ServiceNameTable.COLUMN_SERVICE_NAME, serviceName.getServiceName());

        boolean response = client.prepareIndex(ServiceNameTable.TABLE, serviceName.getId(),source,true);
        logger.debug("save service name register info, application getId: {}, service name: {}, status: {}", serviceName.getId(), serviceName.getServiceName(), response);
    }
}
