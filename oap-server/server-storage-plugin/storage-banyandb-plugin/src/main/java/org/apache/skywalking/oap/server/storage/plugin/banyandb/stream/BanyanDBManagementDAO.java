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

import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.StreamWrite;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.core.analysis.management.ManagementData;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplate;
import org.apache.skywalking.oap.server.core.storage.IManagementDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.schema.BanyanDBStorageDataBuilder;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;

/**
 * UITemplate insertion DAO
 *
 * @param <T> The only ManagementData we have now is {@link UITemplate}
 */
public class BanyanDBManagementDAO<T extends ManagementData> extends AbstractBanyanDBDAO implements IManagementDAO {
    private final BanyanDBStorageDataBuilder<T> storageBuilder;

    public BanyanDBManagementDAO(BanyanDBStorageClient client, BanyanDBStorageDataBuilder<T> storageBuilder) {
        super(client);
        this.storageBuilder = storageBuilder;
    }

    @Override
    public void insert(Model model, ManagementData storageData) throws IOException {
        // ensure only insert once
        StreamQueryResponse resp = query(UITemplate.INDEX_NAME,
                Collections.singletonList(UITemplate.NAME),
                new QueryBuilder() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.appendCondition(eq(UITemplate.NAME, storageData.id()));
                    }
                });

        if (resp != null && resp.getElements().size() > 0) {
            return;
        }

        StreamWrite.StreamWriteBuilder streamWrite = this.storageBuilder
                .entity2Storage((T) storageData)
                .name(model.getName())
                .timestamp(Instant.now().toEpochMilli());
        getClient().write(streamWrite.build());
    }
}
