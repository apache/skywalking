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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base;

import org.apache.skywalking.oap.server.core.analysis.config.NoneStream;
import org.apache.skywalking.oap.server.core.analysis.management.ManagementData;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.IManagementDAO;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.INoneStreamDAO;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;

public class StorageEsDAO extends EsDAO implements StorageDAO {

    public StorageEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public IMetricsDAO newMetricsDao(StorageBuilder<Metrics> storageBuilder) {
        return new MetricsEsDAO(getClient(), storageBuilder);
    }

    @Override
    public IRecordDAO newRecordDao(StorageBuilder<Record> storageBuilder) {
        return new RecordEsDAO(getClient(), storageBuilder);
    }

    @Override
    public INoneStreamDAO newNoneStreamDao(StorageBuilder<NoneStream> storageBuilder) {
        return new NoneStreamEsDAO(getClient(), storageBuilder);
    }

    @Override
    public IManagementDAO newManagementDao(final StorageBuilder<ManagementData> storageBuilder) {
        return new ManagementEsDAO(getClient(), storageBuilder);
    }
}
