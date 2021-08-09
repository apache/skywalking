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

import org.apache.skywalking.banyandb.Write;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBSchema;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageConfig;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.client.BanyanDBInsertRequest;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.client.BanyanDBSchemaMapper;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2RecordDAO;

import java.io.IOException;
import java.util.function.Function;

public class BanyanDBRecordDAO extends H2RecordDAO {
    private final BanyanDBSchema schema;
    private final Function<SegmentRecord, Write.EntityValue> mapper;

    BanyanDBRecordDAO(ModuleManager manager, JDBCHikariCPClient h2Client, StorageHashMapBuilder<Record> storageBuilder, BanyanDBStorageConfig config, BanyanDBSchema schema) {
        super(manager, h2Client, storageBuilder, config.getMaxSizeOfArrayColumn(), config.getNumOfSearchableValuesPerTag());
        this.schema = schema;
        this.mapper = new BanyanDBSchemaMapper(schema.getFieldNames());
    }

    @Override
    public InsertRequest prepareBatchInsert(Model model, Record record) throws IOException {
        if (record instanceof SegmentRecord) {
            final SegmentRecord segmentRecord = (SegmentRecord) record;
            Write.WriteRequest request = Write.WriteRequest.newBuilder()
                    .setMetadata(this.schema.getMetadata())
                    .setEntity(this.mapper.apply(segmentRecord))
                    .build();
            return new BanyanDBInsertRequest(request);
        }
        return super.prepareBatchInsert(model, record);
    }
}
