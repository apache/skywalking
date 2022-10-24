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

package org.apache.skywalking.oap.server.core.query;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.RecordCondition;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.Record;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.core.storage.query.IRecordsQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;

public class TopNRecordsQueryService implements Service {
    private final ModuleManager moduleManager;
    private IRecordsQueryDAO recordsQueryDAO;

    public TopNRecordsQueryService(ModuleManager manager) {
        this.moduleManager = manager;
    }

    private IRecordsQueryDAO getRecordsQueryDAO() {
        if (recordsQueryDAO == null) {
            this.recordsQueryDAO = moduleManager.find(StorageModule.NAME)
                                                    .provider()
                                                    .getService(IRecordsQueryDAO.class);
        }
        return recordsQueryDAO;
    }

    public List<SelectedRecord> readSampledRecords(TopNCondition condition, Duration duration) throws IOException {
        final List<Record> records = getRecordsQueryDAO().readRecords(
            new RecordCondition(condition), ValueColumnMetadata.INSTANCE.getValueCName(condition.getName()), duration);
        return records.stream().filter(Objects::nonNull).map(Record::toSelectedRecord).collect(Collectors.toList());
    }
}
