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

package org.apache.skywalking.apm.collector.storage.h2.dao.register;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.register.IServiceNameRegisterDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceName;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceNameTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class ServiceNameRegisterH2DAO extends H2DAO implements IServiceNameRegisterDAO {

    private final Logger logger = LoggerFactory.getLogger(ServiceNameRegisterH2DAO.class);

    public ServiceNameRegisterH2DAO(H2Client client) {
        super(client);
    }

    @Override
    public int getMaxServiceId() {
        return getMaxId(ServiceNameTable.TABLE, ServiceNameTable.SERVICE_ID.getName());
    }

    @Override
    public int getMinServiceId() {
        return getMinId(ServiceNameTable.TABLE, ServiceNameTable.SERVICE_ID.getName());
    }

    @Override
    public void save(ServiceName serviceName) {
        logger.debug("save service name register info, application getApplicationId: {}, service name: {}", serviceName.getId(), serviceName.getServiceName());
        H2Client client = getClient();
        Map<String, Object> target = new HashMap<>();
        target.put(ServiceNameTable.ID.getName(), serviceName.getId());
        target.put(ServiceNameTable.SERVICE_ID.getName(), serviceName.getServiceId());
        target.put(ServiceNameTable.APPLICATION_ID.getName(), serviceName.getApplicationId());
        target.put(ServiceNameTable.SERVICE_NAME.getName(), serviceName.getServiceName());
        target.put(ServiceNameTable.SRC_SPAN_TYPE.getName(), serviceName.getSrcSpanType());

        String sql = SqlBuilder.buildBatchInsertSql(ServiceNameTable.TABLE, target.keySet());
        Object[] params = target.values().toArray(new Object[0]);
        try {
            client.execute(sql, params);
        } catch (H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
