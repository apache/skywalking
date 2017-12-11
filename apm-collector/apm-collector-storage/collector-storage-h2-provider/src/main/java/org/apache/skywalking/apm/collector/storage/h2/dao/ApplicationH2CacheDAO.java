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
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationCacheDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.table.register.ApplicationTable;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class ApplicationH2CacheDAO extends H2DAO implements IApplicationCacheDAO {

    private final Logger logger = LoggerFactory.getLogger(ApplicationH2CacheDAO.class);
    private static final String GET_APPLICATION_ID_OR_CODE_SQL = "select {0} from {1} where {2} = ?";

    public ApplicationH2CacheDAO(H2Client client) {
        super(client);
    }

    @Override
    public int getApplicationId(String applicationCode) {
        logger.info("get the application getId with application code = {}", applicationCode);
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_APPLICATION_ID_OR_CODE_SQL, ApplicationTable.COLUMN_APPLICATION_ID, ApplicationTable.TABLE, ApplicationTable.COLUMN_APPLICATION_CODE);

        Object[] params = new Object[] {applicationCode};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return 0;
    }

    @Override public String getApplicationCode(int applicationId) {
        logger.debug("get application code, applicationId: {}", applicationId);
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_APPLICATION_ID_OR_CODE_SQL, ApplicationTable.COLUMN_APPLICATION_CODE, ApplicationTable.TABLE, ApplicationTable.COLUMN_APPLICATION_ID);
        Object[] params = new Object[] {applicationId};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return Const.EMPTY_STRING;
    }
}
