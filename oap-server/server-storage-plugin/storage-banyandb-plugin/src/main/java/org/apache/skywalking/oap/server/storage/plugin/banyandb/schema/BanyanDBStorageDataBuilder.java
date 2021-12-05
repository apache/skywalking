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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.schema;

import org.apache.skywalking.banyandb.v1.Banyandb;
import org.apache.skywalking.banyandb.v1.client.SerializableTag;
import org.apache.skywalking.banyandb.v1.client.StreamWrite;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.model.Model;

import java.util.Collections;
import java.util.List;

public abstract class BanyanDBStorageDataBuilder<T extends StorageData> {
    public StreamWrite entity2Storage(Model model, T entity) {
        return StreamWrite.builder()
                .elementId(this.elementID(entity))
                .name(model.getName())
                .timestamp(this.timestamp(model, entity))
                .searchableTags(this.searchableTags(entity))
                .dataTags(this.dataTags(entity))
                .build();
    }

    protected String elementID(T entity) {
        return entity.id();
    }

    abstract protected long timestamp(Model model, T entity);

    abstract protected List<SerializableTag<Banyandb.TagValue>> searchableTags(T entity);

    protected List<SerializableTag<Banyandb.TagValue>> dataTags(T entity) {
        return Collections.emptyList();
    }
}
