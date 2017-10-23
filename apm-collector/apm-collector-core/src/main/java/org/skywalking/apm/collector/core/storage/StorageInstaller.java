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

package org.skywalking.apm.collector.core.storage;

import java.util.List;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public abstract class StorageInstaller {

    private final Logger logger = LoggerFactory.getLogger(StorageInstaller.class);

    public final void install(Client client) throws StorageException {
        StorageDefineLoader defineLoader = new StorageDefineLoader();
        try {
            List<TableDefine> tableDefines = defineLoader.load();
            defineFilter(tableDefines);
            Boolean debug = System.getProperty("debug") != null;

            for (TableDefine tableDefine : tableDefines) {
                tableDefine.initialize();
                if (!isExists(client, tableDefine)) {
                    logger.info("table: {} not exists", tableDefine.getName());
                    createTable(client, tableDefine);
                } else if (debug) {
                    logger.info("table: {} exists", tableDefine.getName());
                    deleteTable(client, tableDefine);
                    createTable(client, tableDefine);
                }
            }
        } catch (DefineException e) {
            throw new StorageInstallException(e.getMessage(), e);
        }
    }

    protected abstract void defineFilter(List<TableDefine> tableDefines);

    protected abstract boolean isExists(Client client, TableDefine tableDefine) throws StorageException;

    protected abstract boolean deleteTable(Client client, TableDefine tableDefine) throws StorageException;

    protected abstract boolean createTable(Client client, TableDefine tableDefine) throws StorageException;
}
