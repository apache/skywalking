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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import io.grpc.Status;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.v1.client.BanyanDBClient;
import org.apache.skywalking.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.banyandb.v1.client.metadata.Catalog;
import org.apache.skywalking.banyandb.v1.client.metadata.Duration;
import org.apache.skywalking.banyandb.v1.client.metadata.Group;
import org.apache.skywalking.banyandb.v1.client.metadata.IndexRule;
import org.apache.skywalking.banyandb.v1.client.metadata.NamedSchema;
import org.apache.skywalking.banyandb.v1.client.metadata.Stream;
import org.apache.skywalking.banyandb.v1.client.metadata.TagFamilySpec;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public enum MetadataRegistry {
    INSTANCE;

    private final Map<String, PartialMetadata> registry = new ConcurrentHashMap<>();

    public NamedSchema<?> registerModel(Model model, ConfigService configService) {
        PartialMetadata partialMetadata = parseMetadata(model);
        final Stream.Builder builder = Stream.create(partialMetadata.getGroup(), partialMetadata.getName());
        Map<String, ModelColumn> modelColumnMap = model.getColumns().stream()
                .collect(Collectors.toMap(modelColumn -> modelColumn.getColumnName().getStorageName(), Function.identity()));
        // parse and set sharding keys
        builder.setEntityRelativeTags(parseEntityNames(modelColumnMap));
        // parse and set tag families, which contains tag specs
        List<TagFamilySpec> specs = parseTagFamilySpecs(model, partialMetadata, configService);
        builder.addTagFamilies(specs);
        // parse and add index definition
        builder.addIndexes(parseIndexRules(specs, partialMetadata.indexFamily(), modelColumnMap));

        registry.put(model.getName(), partialMetadata);
        return builder.build();
    }

    public PartialMetadata findSchema(final String name) {
        return this.registry.get(name);
    }

    List<IndexRule> parseIndexRules(List<TagFamilySpec> specs, String indexTagFamily, Map<String, ModelColumn> modelColumnMap) {
        List<IndexRule> indexRules = new ArrayList<>();
        for (final TagFamilySpec spec : specs) {
            if (!indexTagFamily.equals(spec.tagFamilyName())) {
                continue;
            }
            for (final TagFamilySpec.TagSpec tagSpec : spec.tagSpecs()) {
                final String tagName = tagSpec.getTagName();
                // TODO: we need to add support index type in the OAP core
                // Currently, we only register INVERTED type
                final ModelColumn modelColumn = modelColumnMap.get(tagName);
                // if it is null, it must be a user-defined tag
                if (modelColumn == null) {
                    indexRules.add(IndexRule.create(tagName, IndexRule.IndexType.INVERTED, IndexRule.IndexLocation.SERIES));
                    continue;
                }
                if (modelColumn.getBanyanDBExtension().isGlobalIndexing()) {
                    indexRules.add(IndexRule.create(tagName, IndexRule.IndexType.INVERTED, IndexRule.IndexLocation.GLOBAL));
                } else {
                    indexRules.add(IndexRule.create(tagName, IndexRule.IndexType.INVERTED, IndexRule.IndexLocation.SERIES));
                }
            }
        }
        return indexRules;
    }

    /**
     * Parse sharding keys from the {@link Model}
     *
     * @param modelColumnMap the mapping between column storageName and {@link ModelColumn}
     * @return a list of column names in strict order
     */
    List<String> parseEntityNames(Map<String, ModelColumn> modelColumnMap) {
        List<ModelColumn> shardingColumns = new ArrayList<>();
        for (final ModelColumn col : modelColumnMap.values()) {
            if (col.getBanyanDBExtension().isShardingKey()) {
                shardingColumns.add(col);
            }
        }
        return shardingColumns.stream()
                .sorted(Comparator.comparingInt(col -> col.getBanyanDBExtension().getShardingKeyIdx()))
                .map(col -> col.getColumnName().getName())
                .collect(Collectors.toList());
    }

    List<TagFamilySpec> parseTagFamilySpecs(Model model, PartialMetadata metadata, ConfigService configService) {
        Map<String, TagFamilySpec.Builder> builderMap = new HashMap<>();
        for (final ModelColumn col : model.getColumns()) {
            final TagFamilySpec.TagSpec tagSpec = parseTagSpec(col);
            if (tagSpec == null) {
                continue;
            }
            if (col.shouldIndex()) {
                builderMap.computeIfAbsent(metadata.indexFamily(), TagFamilySpec::create).addTagSpec(tagSpec);
            } else {
                builderMap.computeIfAbsent(metadata.nonIndexFamily(), TagFamilySpec::create).addTagSpec(tagSpec);
            }
        }

        // add all user-defined indexed tags to the end of the "searchable" family
        if (SegmentRecord.INDEX_NAME.equals(model.getName())) {
            builderMap.computeIfAbsent(metadata.indexFamily(), TagFamilySpec::create).addTagSpecs(parseExtraTagSpecs(configService.getSearchableTracesTags()));
        } else if (LogRecord.INDEX_NAME.equals(model.getName())) {
            builderMap.computeIfAbsent(metadata.indexFamily(), TagFamilySpec::create).addTagSpecs(parseExtraTagSpecs(configService.getSearchableLogsTags()));
        } else if (AlarmRecord.INDEX_NAME.equals(model.getName())) {
            builderMap.computeIfAbsent(metadata.indexFamily(), TagFamilySpec::create).addTagSpecs(parseExtraTagSpecs(configService.getSearchableAlarmTags()));
        }

        return builderMap.values().stream().map(TagFamilySpec.Builder::build).collect(Collectors.toList());
    }

    /**
     * Extract extra tags from Configuration.
     * They are for tags defined for {@link SegmentRecord}, {@link LogRecord} and {@link AlarmRecord}.
     *
     * @param tags a series of tags joint by comma
     * @return a list of {@link org.apache.skywalking.banyandb.v1.client.metadata.TagFamilySpec.TagSpec} generated from input
     */
    private List<TagFamilySpec.TagSpec> parseExtraTagSpecs(String tags) {
        if (StringUtil.isEmpty(tags)) {
            return Collections.emptyList();
        }
        String[] tagsArray = tags.split(",");
        if (tagsArray.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.stream(tagsArray)
                .map(TagFamilySpec.TagSpec::newStringTag)
                .collect(Collectors.toList());
    }

    /**
     * Parse TagSpec from {@link ModelColumn}
     *
     * @param modelColumn the column in the model to be parsed
     * @return a typed tag spec
     */
    @Nullable
    private TagFamilySpec.TagSpec parseTagSpec(ModelColumn modelColumn) {
        final Class<?> clazz = modelColumn.getType();
        if (String.class.equals(clazz)) {
            return TagFamilySpec.TagSpec.newStringTag(modelColumn.getColumnName().getStorageName());
        } else if (int.class.equals(clazz) || long.class.equals(clazz)) {
            return TagFamilySpec.TagSpec.newIntTag(modelColumn.getColumnName().getStorageName());
        } else if (byte[].class.equals(clazz) || DataTable.class.equals(clazz)) {
            return TagFamilySpec.TagSpec.newBinaryTag(modelColumn.getColumnName().getStorageName());
        } else {
            // TODO: we skip all tags with type of List<String>
            if ("tags".equals(modelColumn.getColumnName().getStorageName())) {
                return null;
            }
            throw new IllegalStateException("type " + modelColumn.getType().toString() + " is not supported");
        }
    }

    public PartialMetadata parseMetadata(Model model) {
        if (model.isRecord()) {
            String group = "stream-default";
            if (model.isSuperDataset()) {
                // for superDataset, we should use separate group
                group = "stream-" + model.getName();
            }
            return new PartialMetadata(group, model.getName(), Kind.STREAM);
        }
        return new PartialMetadata("measure-default", model.getName(), Kind.MEASURE);
    }

    @RequiredArgsConstructor
    @Data
    public static class PartialMetadata {
        private final String group;
        private final String name;
        private final Kind kind;

        public Optional<NamedSchema<?>> findRemoteSchema(BanyanDBClient client) throws BanyanDBException {
            try {
                switch (kind) {
                    case STREAM:
                        return Optional.ofNullable(client.findStream(this.group, this.name));
                    case MEASURE:
                        return Optional.ofNullable(client.findMeasure(this.group, this.name));
                    default:
                        throw new IllegalStateException("should not reach here");
                }
            } catch (BanyanDBException ex) {
                if (ex.getStatus().equals(Status.Code.NOT_FOUND)) {
                    return Optional.empty();
                }

                throw ex;
            }
        }

        public Group getOrCreateGroup(BanyanDBClient client) throws BanyanDBException {
            Group g = client.findGroup(this.group);
            if (g != null) {
                return g;
            }
            switch (kind) {
                case STREAM:
                    return client.define(Group.create(this.group, Catalog.STREAM, 2, 0, Duration.ofDays(7)));
                case MEASURE:
                    return client.define(Group.create(this.group, Catalog.MEASURE, 2, 12, Duration.ofDays(7)));
                default:
                    throw new IllegalStateException("should not reach here");
            }
        }

        public String indexFamily() {
            switch (kind) {
                case MEASURE:
                    return "default";
                case STREAM:
                    return "searchable";
                default:
                    throw new IllegalStateException("should not reach here");
            }
        }

        public String nonIndexFamily() {
            switch (kind) {
                case MEASURE:
                    return null;
                case STREAM:
                    return "binary";
                default:
                    throw new IllegalStateException("should not reach here");
            }
        }
    }

    public enum Kind {
        MEASURE, STREAM;
    }
}
