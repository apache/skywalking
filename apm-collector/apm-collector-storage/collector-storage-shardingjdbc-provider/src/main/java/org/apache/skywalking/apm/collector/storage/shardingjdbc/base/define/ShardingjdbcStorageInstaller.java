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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.base.define;

import java.util.List;

import org.apache.skywalking.apm.collector.client.Client;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClientException;
import org.apache.skywalking.apm.collector.core.data.TableDefine;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.StorageException;
import org.apache.skywalking.apm.collector.storage.StorageInstallException;
import org.apache.skywalking.apm.collector.storage.StorageInstaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author linjiaqi
 */
public class ShardingjdbcStorageInstaller extends StorageInstaller {

    private static final Logger logger = LoggerFactory.getLogger(ShardingjdbcStorageInstaller.class);

    public ShardingjdbcStorageInstaller(boolean isHighPerformanceMode) {
        super(isHighPerformanceMode);
    }

    @Override protected void defineFilter(List<TableDefine> tableDefines) {
        int size = tableDefines.size();
        for (int i = size - 1; i >= 0; i--) {
            if (!(tableDefines.get(i) instanceof ShardingjdbcTableDefine)) {
                tableDefines.remove(i);
            }
        }
    }

    @Override protected boolean isExists(Client client, TableDefine tableDefine) throws StorageException {
        logger.info("check if table {} exist ", tableDefine.getName());
        return false;
    }

    @Override protected void columnCheck(Client client, TableDefine tableDefine) throws StorageException {

    }

    @Override protected void deleteTable(Client client, TableDefine tableDefine) throws StorageException {
        ShardingjdbcClient shardingjdbcClient = (ShardingjdbcClient)client;
        try {
            shardingjdbcClient.execute("DROP TABLE IF EXISTS " + tableDefine.getName());
        } catch (ShardingjdbcClientException e) {
            throw new StorageInstallException(e.getMessage(), e);
        }
    }

    @Override protected void createTable(Client client, TableDefine tableDefine) throws StorageException {
        ShardingjdbcClient shardingjdbcClient = (ShardingjdbcClient)client;
        ShardingjdbcTableDefine shardingjdbcTableDefine = (ShardingjdbcTableDefine)tableDefine;

        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("CREATE TABLE IF NOT EXISTS ").append(shardingjdbcTableDefine.getName()).append(" (");

        shardingjdbcTableDefine.getColumnDefines().forEach(columnDefine -> {
            ShardingjdbcColumnDefine shardingjdbcColumnDefine = (ShardingjdbcColumnDefine)columnDefine;
            if (shardingjdbcColumnDefine.getType().equals(ShardingjdbcColumnDefine.Type.Varchar.name())) {
                sqlBuilder.append(shardingjdbcColumnDefine.getColumnName().getName()).append(" ").append(shardingjdbcColumnDefine.getType()).append("(255),");
            } else {
                sqlBuilder.append(shardingjdbcColumnDefine.getColumnName().getName()).append(" ").append(shardingjdbcColumnDefine.getType()).append(",");
            }
        });
        sqlBuilder.append(" PRIMARY KEY (id)");
        if (shardingjdbcTableDefine.getIndex() != null) {
            sqlBuilder.append(", KEY " + shardingjdbcTableDefine.getName() + Const.ID_SPLIT + shardingjdbcTableDefine.getIndex() + " (" + shardingjdbcTableDefine.getIndex() + ")");
        }
        sqlBuilder.append(")");
        try {
            logger.info("create if not exists shardingjdbc table with sql {}", sqlBuilder);
            shardingjdbcClient.execute(sqlBuilder.toString());
        } catch (ShardingjdbcClientException e) {
            throw new StorageInstallException(e.getMessage(), e);
        }
    }
}
