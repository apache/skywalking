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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.IServiceEntryUIDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceEntryTable;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.core.util.ColumnNameUtils;
import org.apache.skywalking.apm.collector.core.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ServiceEntryH2UIDAO extends H2DAO implements IServiceEntryUIDAO {

    private final Logger logger = LoggerFactory.getLogger(ServiceEntryH2UIDAO.class);
    private static final String GET_SERVICE_ENTRY_SQL = "select * from {0} where {1} >= ? and {2} <= ?";

    public ServiceEntryH2UIDAO(H2Client client) {
        super(client);
    }

    @Override public JsonObject load(int applicationId, String entryServiceName, long startTime, long endTime, int from,
        int size) {
        H2Client client = getClient();
        String sql = GET_SERVICE_ENTRY_SQL;
        List<Object> params = new ArrayList<>();
        List<Object> columns = new ArrayList<>();
        columns.add(ServiceEntryTable.TABLE);
        columns.add(ServiceEntryTable.COLUMN_NEWEST_TIME);
        columns.add(ServiceEntryTable.COLUMN_REGISTER_TIME);
        params.add(startTime);
        params.add(endTime);
        int paramIndex = 2;
        if (applicationId != 0) {
            paramIndex++;
            sql = sql + " and {" + paramIndex + "} = ?";
            params.add(applicationId);
            columns.add(ServiceEntryTable.COLUMN_APPLICATION_ID);
        }
        if (StringUtils.isNotEmpty(entryServiceName)) {
            paramIndex++;
            sql = sql + " and {" + paramIndex + "} = ?";
            params.add(entryServiceName);
            columns.add(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME);
        }
        sql = sql + " limit " + from + "," + size;
        sql = SqlBuilder.buildSql(sql, columns);
        Object[] p = params.toArray(new Object[0]);
        JsonArray serviceArray = new JsonArray();
        JsonObject response = new JsonObject();
        int index = 0;
        try (ResultSet rs = client.executeQuery(sql, p)) {
            while (rs.next()) {
                int appId = rs.getInt(ServiceEntryTable.COLUMN_APPLICATION_ID);
                int entryServiceId = rs.getInt(ServiceEntryTable.COLUMN_ENTRY_SERVICE_ID);
                String entryServiceName1 = rs.getString(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME);

                JsonObject row = new JsonObject();
                row.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceEntryTable.COLUMN_ENTRY_SERVICE_ID), entryServiceId);
                row.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME), entryServiceName1);
                row.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceEntryTable.COLUMN_APPLICATION_ID), appId);
                serviceArray.add(row);
                index++;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        response.addProperty("total", index);
        response.add("array", serviceArray);

        return response;
    }
}
