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

import lombok.RequiredArgsConstructor;
import org.apache.skywalking.banyandb.v1.client.StreamWrite;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.config.NoneStream;
import org.apache.skywalking.oap.server.core.storage.INoneStreamDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.schema.BanyanDBStorageDataBuilder;

import java.io.IOException;

/**
 * DAO for NoneStream, specifically ProfileTaskRecord
 *
 * @param <T> For NoneStream, we only have {@link org.apache.skywalking.oap.server.core.profile.ProfileTaskRecord}
 */
@RequiredArgsConstructor
public class BanyanDBNoneStreamDAO<T extends NoneStream> implements INoneStreamDAO {
    private final BanyanDBStorageClient client;
    private final BanyanDBStorageDataBuilder<T> storageBuilder;

    @Override
    public void insert(Model model, NoneStream noneStream) throws IOException {
        final long timestamp = TimeBucket.getTimeBucket(noneStream.getTimeBucket(), model.getDownsampling());
        StreamWrite.StreamWriteBuilder builder =
                this.storageBuilder.entity2Storage((T) noneStream)
                        .name(model.getName())
                        .timestamp(timestamp);
        this.client.write(builder.build());
    }
}
