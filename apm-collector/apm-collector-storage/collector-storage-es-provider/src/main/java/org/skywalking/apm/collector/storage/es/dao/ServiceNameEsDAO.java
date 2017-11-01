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
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.core.data.Data;
import org.skywalking.apm.collector.storage.dao.IServiceNameDAO;
import org.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.skywalking.apm.collector.storage.table.register.ServiceNameDataDefine;
import org.skywalking.apm.collector.storage.table.register.ServiceNameTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ServiceNameEsDAO extends EsDAO implements IServiceNameDAO {

    private final Logger logger = LoggerFactory.getLogger(ServiceNameEsDAO.class);

    @Override public int getMaxServiceId() {
        return getMaxId(ServiceNameTable.TABLE, ServiceNameTable.COLUMN_SERVICE_ID);
    }

    @Override public int getMinServiceId() {
        return getMinId(ServiceNameTable.TABLE, ServiceNameTable.COLUMN_SERVICE_ID);
    }

    @Override public void save(Data data) {
        String id = ServiceNameDataDefine.ServiceName.INSTANCE.getId(data);
        int applicationId = ServiceNameDataDefine.ServiceName.INSTANCE.getApplicationId(data);
        int serviceId = ServiceNameDataDefine.ServiceName.INSTANCE.getServiceId(data);
        String serviceName = ServiceNameDataDefine.ServiceName.INSTANCE.getServiceName(data);

        logger.debug("save service name register info, application id: {}, service name: {}", applicationId, serviceName);
        ElasticSearchClient client = getClient();
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceNameTable.COLUMN_SERVICE_ID, serviceId);
        source.put(ServiceNameTable.COLUMN_APPLICATION_ID, applicationId);
        source.put(ServiceNameTable.COLUMN_SERVICE_NAME, serviceName);

        IndexResponse response = client.prepareIndex(ServiceNameTable.TABLE, id).setSource(source).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE).get();
        logger.debug("save service name register info, application id: {}, service name: {}, status: {}", applicationId, serviceName, response.status().name());
    }
}
