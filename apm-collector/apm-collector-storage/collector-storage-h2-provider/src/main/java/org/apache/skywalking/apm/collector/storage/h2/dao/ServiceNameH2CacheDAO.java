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


package org.apache.skywalking.apm.collector.storage.h2.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.storage.dao.IServiceNameCacheDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceNameTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class ServiceNameH2CacheDAO extends H2DAO implements IServiceNameCacheDAO {

    private final Logger logger = LoggerFactory.getLogger(ServiceNameH2CacheDAO.class);

    private static final String GET_SERVICE_NAME_SQL = "select {0},{1} from {2} where {3} = ?";
    private static final String GET_SERVICE_ID_SQL = "select {0} from {1} where {2} = ? and {3} = ? limit 1";

    public ServiceNameH2CacheDAO(H2Client client) {
        super(client);
    }

    @Override public String getServiceName(int serviceId) {
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_SERVICE_NAME_SQL, ServiceNameTable.COLUMN_APPLICATION_ID, ServiceNameTable.COLUMN_SERVICE_NAME,
            ServiceNameTable.TABLE, ServiceNameTable.COLUMN_SERVICE_ID);
        Object[] params = new Object[] {serviceId};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                String serviceName = rs.getString(ServiceNameTable.COLUMN_SERVICE_NAME);
                int applicationId = rs.getInt(ServiceNameTable.COLUMN_APPLICATION_ID);
                return applicationId + Const.ID_SPLIT + serviceName;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return Const.EMPTY_STRING;
    }

    @Override public int getServiceId(int applicationId, String serviceName) {
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_SERVICE_ID_SQL, ServiceNameTable.COLUMN_SERVICE_ID,
            ServiceNameTable.TABLE, ServiceNameTable.COLUMN_APPLICATION_ID, ServiceNameTable.COLUMN_SERVICE_NAME);
        Object[] params = new Object[] {applicationId, serviceName};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                return rs.getInt(ServiceNameTable.COLUMN_SERVICE_ID);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return 0;
    }
}
