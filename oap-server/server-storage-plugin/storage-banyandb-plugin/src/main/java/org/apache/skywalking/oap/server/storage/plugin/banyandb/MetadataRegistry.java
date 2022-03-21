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

import com.google.common.collect.ImmutableSet;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase;
import org.apache.skywalking.banyandb.v1.client.metadata.IndexRule;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public enum MetadataRegistry {
    INSTANCE;

    private final Map<String, StreamMetadata> streams = new HashMap<>();

    public StreamMetadata registerModel(Model model, ConfigService configService) {
        BanyandbDatabase.Stream pbStream = parseStreamFromModel(model, configService);

        final boolean useIdAsEntity = pbStream.getEntity().getTagNamesCount() == 1 &&
                StreamMetadata.ID.equals(pbStream.getEntity().getTagNames(0));

        final Stream stream = new Stream(pbStream.getMetadata().getGroup(), pbStream.getMetadata().getName());

        List<IndexRule> indexRules = new ArrayList<>();
        Set<String> entityNameSet = ImmutableSet.copyOf(pbStream.getEntity().getTagNamesList());
        stream.setEntityTagNames(pbStream.getEntity().getTagNamesList());

        Map<String, StreamMetadata.TagMetadata> tagDefinition = new HashMap<>();

        for (BanyandbDatabase.TagFamilySpec pbTagFamilySpec : pbStream.getTagFamiliesList()) {
            final TagFamilySpec tagFamilySpec = TagFamilySpec.fromProtobuf(pbTagFamilySpec);
            stream.addTagFamilySpec(tagFamilySpec);

            int tagIndex = 0;
            for (final TagFamilySpec.TagSpec tagSpec : tagFamilySpec.getTagSpecs()) {
                // register tag
                tagDefinition.put(tagSpec.getTagName(), new StreamMetadata.TagMetadata(tagFamilySpec.getTagFamilyName(), tagSpec, tagIndex++));

                // if the tag family equals to "searchable", build index rules
                if (tagFamilySpec.getTagFamilyName().equals(StreamMetadata.TAG_FAMILY_SEARCHABLE)) {
                    // check if this spec exists in the entity names
                    if (entityNameSet.contains(tagSpec.getTagName())) {
                        continue;
                    }
                    BanyandbDatabase.IndexRule pbIndexRule = parseIndexRuleFromTagSpec(pbStream.getMetadata(), tagSpec);
                    IndexRule indexRule = IndexRule.fromProtobuf(pbIndexRule);
                    indexRules.add(indexRule);
                }
            }
        }

        StreamMetadata streamMetadata = StreamMetadata.builder().model(model).stream(stream)
                .tagDefinition(tagDefinition)
                .indexRules(indexRules)
                .group(pbStream.getMetadata().getGroup())
                .useIdAsEntity(useIdAsEntity)
                .build();
        streams.put(model.getName(), streamMetadata);
        return streamMetadata;
    }

    public StreamMetadata findStreamMetadata(final String name) {
        return this.streams.get(name);
    }

    private BanyandbDatabase.Stream parseStreamFromModel(Model model, ConfigService configService) {
        List<ModelColumn> shardingColumns = new ArrayList<>();

        List<BanyandbDatabase.TagSpec> searchableTagsSpecs = new ArrayList<>();
        List<BanyandbDatabase.TagSpec> dataTagsSpecs = new ArrayList<>();
        for (final ModelColumn modelColumn : model.getColumns()) {
            if (modelColumn.getShardingKeyIdx() > -1) {
                shardingColumns.add(modelColumn);
            }
            if (modelColumn.isIndexOnly()) {
                // skip
            } else if (modelColumn.isStorageOnly()) {
                dataTagsSpecs.add(parseTagSpecFromModelColumn(modelColumn));
            } else {
                searchableTagsSpecs.add(parseTagSpecFromModelColumn(modelColumn));
            }
        }

        Set<String> entities = shardingColumns.stream()
                .sorted(Comparator.comparingInt(ModelColumn::getShardingKeyIdx))
                .map(modelColumn -> modelColumn.getColumnName().getStorageName())
                .collect(Collectors.toSet());

        if (entities.isEmpty()) {
            // if sharding keys are not defined, we have to use ID
            entities = Collections.singleton(StreamMetadata.ID);
            // append ID
            searchableTagsSpecs.add(BanyandbDatabase.TagSpec.newBuilder()
                    .setName(StreamMetadata.ID)
                    .setType(BanyandbDatabase.TagType.TAG_TYPE_STRING).build());
        }

        // add all user-defined indexed tags to the end of the "searchable" family
        if (SegmentRecord.INDEX_NAME.equals(model.getName())) {
            searchableTagsSpecs.addAll(parseTagSpecsFromConfiguration(configService.getSearchableTracesTags()));
        } else if (LogRecord.INDEX_NAME.equals(model.getName())) {
            searchableTagsSpecs.addAll(parseTagSpecsFromConfiguration(configService.getSearchableLogsTags()));
        } else if (AlarmRecord.INDEX_NAME.equals(model.getName())) {
            searchableTagsSpecs.addAll(parseTagSpecsFromConfiguration(configService.getSearchableAlarmTags()));
        }

        String group = "default-stream";
        if (model.isSuperDataset()) {
            // for superDataset, we should use separate group
            group = model.getName() + "-stream";
        }

        return BanyandbDatabase.Stream.newBuilder()
                .addTagFamilies(BanyandbDatabase.TagFamilySpec.newBuilder()
                        .setName(StreamMetadata.TAG_FAMILY_DATA)
                        .addAllTags(dataTagsSpecs)
                        .build())
                .addTagFamilies(BanyandbDatabase.TagFamilySpec.newBuilder()
                        .setName(StreamMetadata.TAG_FAMILY_SEARCHABLE)
                        .addAllTags(searchableTagsSpecs)
                        .build())
                .setEntity(BanyandbDatabase.Entity.newBuilder()
                        .addAllTagNames(entities)
                        .build())
                .setMetadata(BanyandbCommon.Metadata.newBuilder()
                        .setGroup(group)
                        .setName(model.getName())
                        .build())
                .build();
    }

    private BanyandbDatabase.TagSpec parseTagSpecFromModelColumn(ModelColumn modelColumn) {
        final Class<?> clazz = modelColumn.getType();
        if (String.class.equals(clazz)) {
            return BanyandbDatabase.TagSpec.newBuilder().setName(modelColumn.getColumnName().getStorageName())
                    .setType(BanyandbDatabase.TagType.TAG_TYPE_STRING).build();
        } else if (int.class.equals(clazz) || long.class.equals(clazz)) {
            return BanyandbDatabase.TagSpec.newBuilder().setName(modelColumn.getColumnName().getStorageName())
                    .setType(BanyandbDatabase.TagType.TAG_TYPE_INT).build();
        } else if (byte[].class.equals(clazz) || DataTable.class.equals(clazz)) {
            return BanyandbDatabase.TagSpec.newBuilder().setName(modelColumn.getColumnName().getStorageName())
                    .setType(BanyandbDatabase.TagType.TAG_TYPE_DATA_BINARY).build();
        } else {
            throw new IllegalStateException("type " + modelColumn.getType().toString() + " is not supported");
        }
    }

    private List<BanyandbDatabase.TagSpec> parseTagSpecsFromConfiguration(String tags) {
        if (StringUtil.isEmpty(tags)) {
            return Collections.emptyList();
        }
        String[] tagsArray = tags.split(",");
        if (tagsArray.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.stream(tagsArray)
                .map(tagName -> BanyandbDatabase.TagSpec.newBuilder().setName(tagName)
                        .setType(BanyandbDatabase.TagType.TAG_TYPE_STRING).build())
                .collect(Collectors.toList());
    }

    private BanyandbDatabase.IndexRule parseIndexRuleFromTagSpec(BanyandbCommon.Metadata metadata, TagFamilySpec.TagSpec tagSpec) {
        // In SkyWalking, only "trace_id" should be stored as a global index
        BanyandbDatabase.IndexRule.Location loc = "trace_id".equals(tagSpec.getTagName()) ?
                BanyandbDatabase.IndexRule.Location.LOCATION_GLOBAL :
                BanyandbDatabase.IndexRule.Location.LOCATION_SERIES;

        return BanyandbDatabase.IndexRule.newBuilder()
                .setMetadata(BanyandbCommon.Metadata.newBuilder()
                        .setName(tagSpec.getTagName()).setGroup(metadata.getGroup()))
                .setLocation(loc)
                .addTags(tagSpec.getTagName())
                // TODO: support TYPE_TREE
                .setType(BanyandbDatabase.IndexRule.Type.TYPE_INVERTED)
                .build();
    }
}
