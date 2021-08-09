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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.dao;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.*;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBSchema;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageConfig;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.client.BanyanDBSchemaMapper;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2StorageDAO;

import java.util.Map;

@Slf4j
public class BanyanDBStorageDAO extends H2StorageDAO {
    private final BanyanDBStorageConfig config;
    private final BanyanDBSchemaMapper mapper;

    public BanyanDBStorageDAO(ModuleManager manager, JDBCHikariCPClient h2Client, BanyanDBStorageConfig config, BanyanDBSchema schema) {
        super(manager, h2Client, config.getMaxSizeOfArrayColumn(), config.getNumOfSearchableValuesPerTag());
        this.config = config;
        this.mapper = new BanyanDBSchemaMapper(schema.getFieldNames());
    }

    @Override
    public IMetricsDAO newMetricsDao(StorageBuilder storageBuilder) {
        return super.newMetricsDao(storageBuilder);
    }

    @Override
    public IRecordDAO newRecordDao(StorageBuilder storageBuilder) {
        try {
            if (SegmentRecord.class.equals(storageBuilder.getClass().getMethod("storage2Entity", Map.class).getReturnType())) {
                return new BanyanDBRecordDAO(this.manager, this.h2Client, (StorageHashMapBuilder<Record>) storageBuilder, this.config, this.mapper);
            } else {
                return super.newRecordDao(storageBuilder);
            }
        } catch (NoSuchMethodException noSuchMethodException) {
            log.error("cannot find method storage2Entity", noSuchMethodException);
            throw new RuntimeException("cannot find method storage2Entity");
        }
    }

    @Override
    public INoneStreamDAO newNoneStreamDao(StorageBuilder storageBuilder) {
        return super.newNoneStreamDao(storageBuilder);
    }

    @Override
    public IManagementDAO newManagementDao(StorageBuilder storageBuilder) {
        return super.newManagementDao(storageBuilder);
    }
}
