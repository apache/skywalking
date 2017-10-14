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

package org.skywalking.apm.collector.storage.h2.dao;

import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.storage.dao.DAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

/**
 * @author pengys5
 */
public abstract class H2DAO extends DAO<H2Client> {
    private final Logger logger = LoggerFactory.getLogger(H2DAO.class);
    public final int getMaxId(String tableName, String columnName) {
        String sql = "select max(" + columnName + ") from " + tableName;
        return getIntValueBySQL(sql);
    }

    public final int getMinId(String tableName, String columnName) {
        String sql = "select min(" + columnName + ") from " + tableName;
        return getIntValueBySQL(sql);
    }

    public final int getIntValueBySQL(String sql) {
        H2Client client = getClient();
        try (ResultSet rs = client.executeQuery(sql, null)) {
            if (rs.next()) {
                int id = rs.getInt(1);
                if (id == Integer.MAX_VALUE || id == Integer.MIN_VALUE) {
                    return 0;
                } else {
                    return id;
                }
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return 0;
    }

    public final String getBatchInsertSql(String tableName, Set<String> columnNames) {
        StringBuilder sb = new StringBuilder("insert into ");
        sb.append(tableName).append("(");
        columnNames.forEach((columnName) -> {
            sb.append(columnName).append(",");
        });
        sb.delete(sb.length() - 1, sb.length());
        sb.append(") values(");
        for (int i = 0; i < columnNames.size(); i++) {
            sb.append("?,");
        }
        sb.delete(sb.length() - 1, sb.length());
        sb.append(")");
        return sb.toString();
    }

    public final String getBatchUpdateSql(String tableName, Set<String> columnNames, String whereClauseName) {
        StringBuilder sb = new StringBuilder("update ");
        sb.append(tableName).append(" set ");
        columnNames.forEach((columnName) -> {
            sb.append(columnName).append("=?,");
        });
        sb.delete(sb.length() - 1, sb.length());
        sb.append(" where ").append(whereClauseName).append("=?");
        return sb.toString();
    }
}
