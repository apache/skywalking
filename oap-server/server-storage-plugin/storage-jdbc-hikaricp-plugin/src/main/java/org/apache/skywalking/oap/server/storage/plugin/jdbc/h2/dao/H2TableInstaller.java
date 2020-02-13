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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntKeyLongValueHashMap;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.model.ColumnName;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.model.ModelInstaller;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLBuilder;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;

/**
 * H2 table initialization. Create tables without Indexes. H2 is for the demonstration only, so, keep the logic as
 * simple as possible.
 */
@Slf4j
public class H2TableInstaller extends ModelInstaller {
    public H2TableInstaller(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override
    protected boolean isExists(Client client, Model model) throws StorageException {
        TableMetaInfo.addModel(model);
        JDBCHikariCPClient h2Client = (JDBCHikariCPClient) client;
        try (Connection conn = h2Client.getConnection()) {
            try (ResultSet rset = conn.getMetaData().getTables(null, null, model.getName(), null)) {
                if (rset.next()) {
                    return true;
                }
            }
        } catch (SQLException e) {
            throw new StorageException(e.getMessage(), e);
        } catch (JDBCClientException e) {
            throw new StorageException(e.getMessage(), e);
        }
        return false;
    }

    @Override
    protected void createTable(Client client, Model model) throws StorageException {
        JDBCHikariCPClient h2Client = (JDBCHikariCPClient) client;
        SQLBuilder tableCreateSQL = new SQLBuilder("CREATE TABLE IF NOT EXISTS " + model.getName() + " (");
        tableCreateSQL.appendLine("id VARCHAR(300) PRIMARY KEY, ");
        for (int i = 0; i < model.getColumns().size(); i++) {
            ModelColumn column = model.getColumns().get(i);
            ColumnName name = column.getColumnName();
            tableCreateSQL.appendLine(
                name.getStorageName() + " " + getColumnType(model, name, column.getType()) + (i != model
                    .getColumns()
                    .size() - 1 ? "," : ""));
        }
        tableCreateSQL.appendLine(")");

        if (log.isDebugEnabled()) {
            log.debug("creating table: " + tableCreateSQL.toStringInNewLine());
        }

        try (Connection connection = h2Client.getConnection()) {
            h2Client.execute(connection, tableCreateSQL.toString());
        } catch (JDBCClientException e) {
            throw new StorageException(e.getMessage(), e);
        } catch (SQLException e) {
            throw new StorageException(e.getMessage(), e);
        }

    }

    /**
     * Set up the data type mapping between Java type and H2 database type
     */
    protected String getColumnType(Model model, ColumnName name, Class<?> type) {
        if (Integer.class.equals(type) || int.class.equals(type)) {
            return "INT";
        } else if (Long.class.equals(type) || long.class.equals(type)) {
            return "BIGINT";
        } else if (Double.class.equals(type) || double.class.equals(type)) {
            return "DOUBLE";
        } else if (String.class.equals(type)) {
            return "VARCHAR(2000)";
        } else if (IntKeyLongValueHashMap.class.equals(type)) {
            return "VARCHAR(20000)";
        } else if (byte[].class.equals(type)) {
            if (DefaultScopeDefine.SEGMENT == model.getScopeId()) {
                if (name.getName().equals(SegmentRecord.DATA_BINARY)) {
                    return "MEDIUMTEXT";
                }
            }
            return "VARCHAR(20000)";
        } else {
            throw new IllegalArgumentException("Unsupported data type: " + type.getName());
        }
    }
}
