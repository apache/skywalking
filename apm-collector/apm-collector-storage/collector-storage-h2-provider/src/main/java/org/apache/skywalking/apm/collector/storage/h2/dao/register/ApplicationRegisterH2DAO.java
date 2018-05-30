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
import org.apache.skywalking.apm.collector.storage.dao.register.IApplicationRegisterDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.table.register.Application;
import org.apache.skywalking.apm.collector.storage.table.register.ApplicationTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class ApplicationRegisterH2DAO extends H2DAO implements IApplicationRegisterDAO {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationRegisterH2DAO.class);

    public ApplicationRegisterH2DAO(H2Client client) {
        super(client);
    }

    @Override
    public int getMaxApplicationId() {
        return getMaxId(ApplicationTable.TABLE, ApplicationTable.APPLICATION_ID.getName());
    }

    @Override
    public int getMinApplicationId() {
        return getMinId(ApplicationTable.TABLE, ApplicationTable.APPLICATION_ID.getName());
    }

    @Override
    public void save(Application application) {
        H2Client client = getClient();

        Map<String, Object> target = new HashMap<>();
        target.put(ApplicationTable.ID.getName(), application.getId());
        target.put(ApplicationTable.APPLICATION_CODE.getName(), application.getApplicationCode());
        target.put(ApplicationTable.APPLICATION_ID.getName(), application.getApplicationId());
        target.put(ApplicationTable.ADDRESS_ID.getName(), application.getAddressId());
        target.put(ApplicationTable.IS_ADDRESS.getName(), application.getIsAddress());

        String sql = SqlBuilder.buildBatchInsertSql(ApplicationTable.TABLE, target.keySet());
        Object[] params = target.values().toArray(new Object[0]);
        try {
            client.execute(sql, params);
        } catch (H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
