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

package org.apache.skywalking.oap.server.storage.plugin.influxdb.installer;

import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2TableInstaller;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.*;

/**
 * Some tables, such as Metrics and SegmentRecord, they are stored in InfluxDB.
 * We don't need to create the tables explicitly in InfluxDB.
 * <p>
 * In different with InfluxDB, we must execute DDL for MySQL.
 */
// FIXME need to change to MySQlTableInstaller
public class TableMixInstaller extends H2TableInstaller {

    public TableMixInstaller(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override
    protected boolean isExists(Client client, Model model) throws StorageException {
        TableMetaInfo.addModel(model);
        switch (model.getScopeId()) {
            case SERVICE_INVENTORY:
            case SERVICE_INSTANCE_INVENTORY:
            case NETWORK_ADDRESS:
            case ENDPOINT_INVENTORY:
                return false;
        }
        return true;
    }

}
