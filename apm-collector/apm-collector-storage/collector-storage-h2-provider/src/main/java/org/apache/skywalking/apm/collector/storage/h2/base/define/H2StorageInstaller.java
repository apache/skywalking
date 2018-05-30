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

package org.apache.skywalking.apm.collector.storage.h2.base.define;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.apache.skywalking.apm.collector.client.Client;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.core.data.TableDefine;
import org.apache.skywalking.apm.collector.storage.StorageException;
import org.apache.skywalking.apm.collector.storage.StorageInstallException;
import org.apache.skywalking.apm.collector.storage.StorageInstaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class H2StorageInstaller extends StorageInstaller {

    private final Logger logger = LoggerFactory.getLogger(H2StorageInstaller.class);

    public H2StorageInstaller(boolean isHighPerformanceMode) {
        super(isHighPerformanceMode);
    }

    @Override protected void defineFilter(List<TableDefine> tableDefines) {
        int size = tableDefines.size();
        for (int i = size - 1; i >= 0; i--) {
            if (!(tableDefines.get(i) instanceof H2TableDefine)) {
                tableDefines.remove(i);
            }
        }
    }

    @Override protected boolean isExists(Client client, TableDefine tableDefine) throws StorageException {
        H2Client h2Client = (H2Client)client;
        ResultSet rs = null;
        try {
            logger.info("check if table {} exist ", tableDefine.getName());
            rs = h2Client.getConnection().getMetaData().getTables(null, null, tableDefine.getName().toUpperCase(), null);
            if (rs.next()) {
                return true;
            }
        } catch (SQLException e) {
            throw new StorageInstallException(e.getMessage(), e);
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
            } catch (SQLException e) {
                throw new StorageInstallException(e.getMessage(), e);
            }
        }
        return false;
    }

    @Override protected void columnCheck(Client client, TableDefine tableDefine) throws StorageException {

    }

    @Override protected void deleteTable(Client client, TableDefine tableDefine) throws StorageException {
        H2Client h2Client = (H2Client)client;
        try {
            h2Client.execute("drop table if exists " + tableDefine.getName());
        } catch (H2ClientException e) {
            throw new StorageInstallException(e.getMessage(), e);
        }
    }

    @Override protected void createTable(Client client, TableDefine tableDefine) throws StorageException {
        H2Client h2Client = (H2Client)client;
        H2TableDefine h2TableDefine = (H2TableDefine)tableDefine;

        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("CREATE TABLE ").append(h2TableDefine.getName()).append(" (");

        h2TableDefine.getColumnDefines().forEach(columnDefine -> {
            H2ColumnDefine h2ColumnDefine = (H2ColumnDefine)columnDefine;
            if (h2ColumnDefine.getType().equals(H2ColumnDefine.Type.Varchar.name())) {
                sqlBuilder.append(h2ColumnDefine.getColumnName()).append(" ").append(h2ColumnDefine.getType()).append("(255),");
            } else {
                sqlBuilder.append(h2ColumnDefine.getColumnName()).append(" ").append(h2ColumnDefine.getType()).append(",");
            }
        });
        //remove last comma
        sqlBuilder.delete(sqlBuilder.length() - 1, sqlBuilder.length());
        sqlBuilder.append(")");
        try {
            logger.info("create h2 table with sql {}", sqlBuilder);
            h2Client.execute(sqlBuilder.toString());
        } catch (H2ClientException e) {
            throw new StorageInstallException(e.getMessage(), e);
        }
    }
}
