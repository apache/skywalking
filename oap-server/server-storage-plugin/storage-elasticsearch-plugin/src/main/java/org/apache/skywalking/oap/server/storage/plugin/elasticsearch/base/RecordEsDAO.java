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

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;

public class RecordEsDAO extends EsDAO implements IRecordDAO {
    private final StorageBuilder<Record> storageBuilder;

    public RecordEsDAO(ElasticSearchClient client,
                       StorageBuilder<Record> storageBuilder) {
        super(client);
        this.storageBuilder = storageBuilder;
    }

    @Override
    public InsertRequest prepareBatchInsert(Model model, Record record) throws IOException {
        final ElasticSearchConverter.ToStorage toStorage = new ElasticSearchConverter.ToStorage(model.getName());
        storageBuilder.entity2Storage(record, toStorage);
        Map<String, Object> builder = IndexController.INSTANCE.appendTableColumn(model, toStorage.obtain());
        String modelName = TimeSeriesUtils.writeIndexName(model, record.getTimeBucket());
        String id = IndexController.INSTANCE.generateDocId(model, record.id().build());
        Optional<String> routing = RoutingUtils.getRouting(model, builder);
        return getClient().prepareInsert(modelName, id, routing, builder);
    }
}
