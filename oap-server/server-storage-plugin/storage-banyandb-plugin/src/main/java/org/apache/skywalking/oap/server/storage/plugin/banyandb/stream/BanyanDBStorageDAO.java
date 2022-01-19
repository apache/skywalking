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
import org.apache.skywalking.oap.server.core.analysis.config.NoneStream;
import org.apache.skywalking.oap.server.core.analysis.management.ManagementData;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.storage.AbstractDAO;
import org.apache.skywalking.oap.server.core.storage.IManagementDAO;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.INoneStreamDAO;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.schema.BanyanDBStorageDataBuilder;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public class BanyanDBStorageDAO extends AbstractDAO<BanyanDBStorageClient> implements StorageDAO {
    public BanyanDBStorageDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public IMetricsDAO newMetricsDao(StorageBuilder storageBuilder) {
        // SKIP:
        // 1. OAL runtime metrics builder
        // 2. Analysis Function builder
        if (storageBuilder.getClass().getName().startsWith("org.apache.skywalking.oap.server.core.")) {
            log.warn("metrics builder {} is not supported yet", storageBuilder.getClass());
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
        return new BanyanDBMetricsDAO<>((BanyanDBStorageDataBuilder<Metrics>) storageBuilder);
    }

    @Override
    public IRecordDAO newRecordDao(StorageBuilder storageBuilder) {
        try {
            final Class<?> returnType = storageBuilder.getClass().getDeclaredMethod("storage2Entity", Map.class).getReturnType();
            // FIXME: this is currently a hack to avoid TopN insertion since we will impl TopN later in BanyanDB side
            if (TopN.class.isAssignableFrom(returnType)) {
                return new IRecordDAO() {
                    @Override
                    public InsertRequest prepareBatchInsert(Model model, Record record) throws IOException {
                        return new InsertRequest() {
                        };
                    }
                };
            } else if (returnType.getName().equals("org.apache.skywalking.oap.server.storage.plugin.zipkin.ZipkinSpanRecord")) {
                // SKIP ZipkinSpanRecord
                return new IRecordDAO() {
                    @Override
                    public InsertRequest prepareBatchInsert(Model model, Record record) throws IOException {
                        return new InsertRequest() {
                        };
                    }
                };
            }
        } catch (NoSuchMethodException ex) {
            log.error("fail to get declared method");
        }
        return new BanyanDBRecordDAO<>((BanyanDBStorageDataBuilder<Record>) storageBuilder);
    }

    @Override
    public INoneStreamDAO newNoneStreamDao(StorageBuilder storageBuilder) {
        return new BanyanDBNoneStreamDAO<>(getClient(), (BanyanDBStorageDataBuilder<NoneStream>) storageBuilder);
    }

    @Override
    public IManagementDAO newManagementDao(StorageBuilder storageBuilder) {
        return new BanyanDBManagementDAO<>(getClient(), (BanyanDBStorageDataBuilder<ManagementData>) storageBuilder);
    }
}
