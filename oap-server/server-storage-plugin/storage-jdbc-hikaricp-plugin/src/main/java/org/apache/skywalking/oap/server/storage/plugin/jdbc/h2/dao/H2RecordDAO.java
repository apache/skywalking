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

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.H2StorageConfig;

public class H2RecordDAO extends H2SQLExecutor implements IRecordDAO {
    private final JDBCHikariCPClient h2Client;
    private final StorageHashMapBuilder<Record> storageBuilder;
    private final int maxSizeOfArrayColumn;

    public H2RecordDAO(H2StorageConfig config,
                       ModuleManager manager,
                       JDBCHikariCPClient h2Client,
                       StorageHashMapBuilder<Record> storageBuilder) {
        super(config);
        this.h2Client = h2Client;
        try {
            if (SegmentRecord.class.equals(
                storageBuilder.getClass().getMethod("storage2Entity", Map.class).getReturnType()
            )) {
                this.maxSizeOfArrayColumn = config.getMaxSizeOfArrayColumn();
                final ConfigService configService = manager.find(CoreModule.NAME)
                                                           .provider()
                                                           .getService(ConfigService.class);
                this.storageBuilder = new H2SegmentRecordBuilder(
                    maxSizeOfArrayColumn,
                    config.getNumOfSearchableValuesPerTag(),
                    Arrays.asList(configService.getSearchableTracesTags().split(Const.COMMA))
                );
            } else if (LogRecord.class.equals(
                storageBuilder.getClass().getMethod("storage2Entity", Map.class).getReturnType())) {
                this.maxSizeOfArrayColumn = config.getMaxSizeOfArrayColumn();
                final ConfigService configService = manager.find(CoreModule.NAME)
                                                           .provider()
                                                           .getService(ConfigService.class);
                this.storageBuilder = new H2LogRecordBuilder(
                    maxSizeOfArrayColumn,
                    config.getNumOfSearchableValuesPerTag(),
                    Arrays.asList(configService.getSearchableLogsTags()
                                               .split(Const.COMMA))
                );
            } else if (AlarmRecord.class.equals(
                storageBuilder.getClass().getMethod("storage2Entity", Map.class).getReturnType())) {
                this.maxSizeOfArrayColumn = config.getMaxSizeOfArrayColumn();
                final ConfigService configService = manager.find(CoreModule.NAME)
                                                           .provider()
                                                           .getService(ConfigService.class);
                this.storageBuilder = new H2AlarmRecordBuilder(
                    maxSizeOfArrayColumn,
                    config.getNumOfSearchableValuesPerTag(),
                    Arrays.asList(configService.getSearchableAlarmTags()
                                               .split(Const.COMMA))
                );
            } else {
                this.maxSizeOfArrayColumn = 1;
                this.storageBuilder = storageBuilder;
            }
        } catch (NoSuchMethodException e) {
            throw new UnexpectedException("Can't find the SegmentRecord$Builder.map2Data method.");
        }
    }

    @Override
    public InsertRequest prepareBatchInsert(Model model, Record record) throws IOException {
        return getInsertExecutor(model.getName(), record, storageBuilder, maxSizeOfArrayColumn);
    }
}
