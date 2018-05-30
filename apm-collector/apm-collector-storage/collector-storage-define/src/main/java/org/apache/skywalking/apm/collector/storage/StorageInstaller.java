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

package org.apache.skywalking.apm.collector.storage;

import java.util.List;
import org.apache.skywalking.apm.collector.client.Client;
import org.apache.skywalking.apm.collector.core.data.StorageDefineLoader;
import org.apache.skywalking.apm.collector.core.data.TableDefine;
import org.apache.skywalking.apm.collector.core.define.DefineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public abstract class StorageInstaller {

    private final Logger logger = LoggerFactory.getLogger(StorageInstaller.class);

    private final boolean isHighPerformanceMode;

    public StorageInstaller(boolean isHighPerformanceMode) {
        this.isHighPerformanceMode = isHighPerformanceMode;
    }

    public final void install(Client client) throws StorageException {
        StorageDefineLoader defineLoader = new StorageDefineLoader();
        try {
            List<TableDefine> tableDefines = defineLoader.load();
            defineFilter(tableDefines);
            Boolean debug = System.getProperty("debug") != null;

            for (TableDefine tableDefine : tableDefines) {
                tableDefine.initialize();
                settingHighPerformance(tableDefine);
                if (!isExists(client, tableDefine)) {
                    logger.info("table: {} not exists", tableDefine.getName());
                    createTable(client, tableDefine);
                } else if (debug) {
                    logger.info("table: {} exists", tableDefine.getName());
                    deleteTable(client, tableDefine);
                    createTable(client, tableDefine);
                }
                columnCheck(client, tableDefine);
            }
        } catch (DefineException e) {
            throw new StorageInstallException(e.getMessage(), e);
        }
    }

    private void settingHighPerformance(TableDefine tableDefine) {
        tableDefine.getColumnDefines().forEach(column -> {
            if (isHighPerformanceMode) {
                column.getColumnName().useShortName();
            }
        });
    }

    protected abstract void defineFilter(List<TableDefine> tableDefines);

    protected abstract boolean isExists(Client client, TableDefine tableDefine) throws StorageException;

    protected abstract void columnCheck(Client client, TableDefine tableDefine) throws StorageException;

    protected abstract void deleteTable(Client client, TableDefine tableDefine) throws StorageException;

    protected abstract void createTable(Client client, TableDefine tableDefine) throws StorageException;
}
