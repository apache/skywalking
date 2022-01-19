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

import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.banyandb.v1.client.SerializableTag;
import org.apache.skywalking.banyandb.v1.client.StreamWrite;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BanyanDBStorageDataBuilder<T extends StorageData> implements StorageBuilder<T, StreamWrite.StreamWriteBuilder> {
    @Override
    public T storage2Entity(StreamWrite.StreamWriteBuilder storageData) {
        return null;
    }

    @Override
    public StreamWrite.StreamWriteBuilder entity2Storage(T entity) {
        StreamWrite.StreamWriteBuilder b = StreamWrite.builder()
                .elementId(this.elementID(entity))
                .searchableTags(this.searchableTags(entity))
                .dataTags(this.dataTags(entity));
        Long ts = this.extractTimestamp(entity);
        if (ts != null) {
            b.timestamp(ts);
        }
        return b;
    }

    protected Long extractTimestamp(T entity) {
        return null;
    }

    protected List<SerializableTag<BanyandbModel.TagValue>> filterSearchableTags(List<Tag> rawTags, List<String> indexTags) {
        if (rawTags == null) {
            return Collections.emptyList();
        }
        Map<String, SerializableTag<BanyandbModel.TagValue>> map = new HashMap<>();
        for (final Tag tag : rawTags) {
            map.put(tag.getKey().toLowerCase(), TagAndValue.stringField(tag.getValue()));
        }
        final List<SerializableTag<BanyandbModel.TagValue>> tags = new ArrayList<>();
        for (String indexedTag : indexTags) {
            SerializableTag<BanyandbModel.TagValue> tag = map.get(indexedTag);
            if (tag == null) {
                tags.add(TagAndValue.nullField());
            } else {
                tags.add(tag);
            }
        }

        return tags;
    }

    protected String elementID(T entity) {
        return entity.id();
    }

    abstract protected List<SerializableTag<BanyandbModel.TagValue>> searchableTags(T entity);

    protected List<SerializableTag<BanyandbModel.TagValue>> dataTags(T entity) {
        return Collections.emptyList();
    }
}
