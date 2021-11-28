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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.*;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public class BanyanDBStorageDAO implements StorageDAO {
    @Override
    public IMetricsDAO newMetricsDao(StorageBuilder storageBuilder) {
        return new IMetricsDAO() {
            @Override
            public List<Metrics> multiGet(Model model, List<Metrics> metrics) throws IOException {
                return Collections.emptyList();
            }

            @Override
            public InsertRequest prepareBatchInsert(Model model, Metrics metrics) throws IOException {
                return new InsertRequest() {
                };
            }

            @Override
            public UpdateRequest prepareBatchUpdate(Model model, Metrics metrics) throws IOException {
                return new UpdateRequest() {
                };
            }
        };
    }

    @Override
    public IRecordDAO newRecordDao(StorageBuilder storageBuilder) {
        try {
            if (SegmentRecord.class.equals(storageBuilder.getClass().getMethod("storage2Entity", Map.class).getReturnType())) {
                return new BanyanDBRecordDAO(new BanyanDBSegmentRecordBuilder());
            } else {
                return (model, record) -> new InsertRequest() {
                };
            }
        } catch (NoSuchMethodException noSuchMethodException) {
            log.error("cannot find method storage2Entity", noSuchMethodException);
            throw new RuntimeException("cannot find method storage2Entity");
        }
    }

    @Override
    public INoneStreamDAO newNoneStreamDao(StorageBuilder storageBuilder) {
        return (model, noneStream) -> {
        };
    }

    @Override
    public IManagementDAO newManagementDao(StorageBuilder storageBuilder) {
        return (model, storageData) -> {
        };
    }
}
