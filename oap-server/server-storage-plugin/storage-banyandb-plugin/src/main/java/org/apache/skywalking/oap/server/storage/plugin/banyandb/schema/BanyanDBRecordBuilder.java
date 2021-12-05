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
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.model.Model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BanyanDBRecordBuilder<T extends Record> extends BanyanDBStorageDataBuilder<T> {
    @Override
    protected long timestamp(Model model, T entity) {
        return TimeBucket.getTimestamp(entity.getTimeBucket(), model.getDownsampling());
    }

    protected List<SerializableTag<Banyandb.TagValue>> filterSearchableTags(List<Tag> rawTags, List<String> indexTags) {
        if (rawTags == null) {
            return Collections.emptyList();
        }
        Map<String, SerializableTag<Banyandb.TagValue>> map = new HashMap<>();
        for (final Tag tag : rawTags) {
            map.put(tag.getKey().toLowerCase(), TagAndValue.stringField(tag.getValue()));
        }
        final List<SerializableTag<Banyandb.TagValue>> tags = new ArrayList<>();
        for (String indexedTag : indexTags) {
            SerializableTag<Banyandb.TagValue> tag = map.get(indexedTag);
            if (tag == null) {
                tags.add(TagAndValue.nullField());
            } else {
                tags.add(tag);
            }
        }

        return tags;
    }
}
