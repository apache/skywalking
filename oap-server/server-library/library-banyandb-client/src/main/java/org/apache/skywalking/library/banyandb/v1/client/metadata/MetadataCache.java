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

package org.apache.skywalking.library.banyandb.v1.client.metadata;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Measure;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Stream;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Trace;
import org.apache.skywalking.library.banyandb.v1.client.BanyanDBClient;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.library.banyandb.v1.client.util.CopyOnWriteMap;

public class MetadataCache {
    private final Map<String, EntityMetadata> cache;
    private final BanyanDBClient client;

    public MetadataCache(BanyanDBClient client) {
        this.cache = new CopyOnWriteMap<>();
        this.client = client;
    }

    public EntityMetadata register(Stream stream) {
        if (stream == null) {
            return null;
        }
        EntityMetadata metadata = parse(stream);
        this.cache.put(formatKey(stream.getMetadata().getGroup(), stream.getMetadata().getName()), metadata);
        return metadata;
    }

    public EntityMetadata unregister(Stream stream) {
        if (stream == null) {
            return null;
        }
        return this.cache.remove(formatKey(stream.getMetadata().getGroup(), stream.getMetadata().getName()));
    }

    public EntityMetadata register(Measure measure) {
        if (measure == null) {
            return null;
        }
        EntityMetadata metadata = parse(measure);
        this.cache.put(formatKey(measure.getMetadata().getGroup(), measure.getMetadata().getName()), metadata);
        return metadata;
    }

    public EntityMetadata unregister(Measure measure) {
        if (measure == null) {
            return null;
        }
        return this.cache.remove(formatKey(measure.getMetadata().getGroup(), measure.getMetadata().getName()));
    }

    public EntityMetadata register(Trace trace) {
        if (trace == null) {
            return null;
        }
        EntityMetadata metadata = parse(trace);
        this.cache.put(formatKey(trace.getMetadata().getGroup(), trace.getMetadata().getName()), metadata);
        return metadata;
    }

    public EntityMetadata unregister(Trace trace) {
        if (trace == null) {
            return null;
        }
        return this.cache.remove(formatKey(trace.getMetadata().getGroup(), trace.getMetadata().getName()));
    }

    public EntityMetadata unregister(String group, String name) {
        return this.cache.remove(formatKey(group, name));
    }

    public EntityMetadata findStreamMetadata(String group, String name) throws BanyanDBException {
        EntityMetadata metadata = this.cache.get(formatKey(group, name));
        if (metadata != null) {
            return metadata;
        }
        return this.register(this.client.findStream(group, name));
    }

    public EntityMetadata findMeasureMetadata(String group, String name) throws BanyanDBException {
        EntityMetadata metadata = this.cache.get(formatKey(group, name));
        if (metadata != null) {
            return metadata;
        }
        return this.register(this.client.findMeasure(group, name));
    }

    public EntityMetadata findTraceMetadata(String group, String name) throws BanyanDBException {
        EntityMetadata metadata = this.cache.get(formatKey(group, name));
        if (metadata != null) {
            return metadata;
        }
        return this.register(this.client.findTrace(group, name));
    }

    public EntityMetadata updateStreamFromSever(String group, String name) throws BanyanDBException {
        return register(client.findStream(group, name));
    }

    public EntityMetadata updateMeasureFromSever(String group, String name) throws BanyanDBException {
        return register(client.findMeasure(group, name));
    }

    public EntityMetadata updateTraceFromServer(String group, String name) throws BanyanDBException {
        return register(client.findTrace(group, name));
    }

    static String formatKey(String group, String name) {
        return group + ":" + name;
    }

    static EntityMetadata parse(Stream s) {
        int totalTags = 0;
        final int[] tagFamilyCapacity = new int[s.getTagFamiliesList().size()];
        Map<String, TagInfo> tagInfo = new HashMap<>();
        int k = 0;
        for (int i = 0; i < s.getTagFamiliesList().size(); i++) {
            final String tagFamilyName = s.getTagFamiliesList().get(i).getName();
            tagFamilyCapacity[i] = s.getTagFamiliesList().get(i).getTagsList().size();
            totalTags += tagFamilyCapacity[i];
            for (int j = 0; j < tagFamilyCapacity[i]; j++) {
                tagInfo.put(s.getTagFamiliesList().get(i).getTagsList().get(j).getName(), new TagInfo(tagFamilyName, k++));
            }
        }
        return new EntityMetadata(s.getMetadata().getGroup(), s.getMetadata().getName(), s.getMetadata().getModRevision(), totalTags, 0, tagFamilyCapacity,
                Collections.unmodifiableMap(tagInfo),
                Collections.emptyMap());
    }

    static EntityMetadata parse(Measure m) {
        int totalTags = 0;
        final int[] tagFamilyCapacity = new int[m.getTagFamiliesList().size()];
        final Map<String, TagInfo> tagOffset = new HashMap<>();
        int k = 0;
        for (int i = 0; i < m.getTagFamiliesList().size(); i++) {
            final String tagFamilyName = m.getTagFamiliesList().get(i).getName();
            tagFamilyCapacity[i] = m.getTagFamiliesList().get(i).getTagsList().size();
            totalTags += tagFamilyCapacity[i];
            for (int j = 0; j < tagFamilyCapacity[i]; j++) {
                tagOffset.put(m.getTagFamiliesList().get(i).getTagsList().get(j).getName(), new TagInfo(tagFamilyName, k++));
            }
        }
        final Map<String, Integer> fieldOffset = new HashMap<>();
        for (int i = 0; i < m.getFieldsList().size(); i++) {
            fieldOffset.put(m.getFieldsList().get(i).getName(), i);
        }
        return new EntityMetadata(m.getMetadata().getGroup(), m.getMetadata().getName(), m.getMetadata().getModRevision(), totalTags, m.getFieldsList().size(), tagFamilyCapacity,
                Collections.unmodifiableMap(tagOffset), Collections.unmodifiableMap(fieldOffset));
    }

    static EntityMetadata parse(Trace t) {
        int totalTags = t.getTagsList().size();
        // For trace, we treat all tags as one family (different from stream structure)
        final int[] tagFamilyCapacity = new int[]{totalTags};
        final Map<String, TagInfo> tagOffset = new HashMap<>();
        for (int i = 0; i < t.getTagsList().size(); i++) {
            tagOffset.put(t.getTagsList().get(i).getName(), new TagInfo("trace_tags", i));
        }
        return new EntityMetadata(t.getMetadata().getGroup(), t.getMetadata().getName(), t.getMetadata().getModRevision(), totalTags, 0, tagFamilyCapacity,
                Collections.unmodifiableMap(tagOffset), Collections.emptyMap());
    }

    @Getter
    @RequiredArgsConstructor
    public static class EntityMetadata {
        private final String group;
        private final String name;
        private final long modRevision;
        private final int totalTags;

        private final int totalFields;

        private final int[] tagFamilyCapacity;

        private final Map<String, TagInfo> tagOffset;

        private final Map<String, Integer> fieldOffset;

        public Optional<TagInfo> findTagInfo(String name) {
            return Optional.ofNullable(this.tagOffset.get(name));
        }

        public int findFieldInfo(String name) {
            return this.fieldOffset.get(name);
        }
    }

    @RequiredArgsConstructor
    @Getter
    public static class TagInfo {
        private final String tagFamilyName;
        private final int offset;
    }
}
