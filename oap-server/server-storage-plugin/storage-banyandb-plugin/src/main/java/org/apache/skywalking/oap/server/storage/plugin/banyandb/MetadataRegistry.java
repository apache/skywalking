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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import io.grpc.Status;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.Singular;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon.Group;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon.Metadata;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon.IntervalRule;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon.Catalog;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon.ResourceOpts;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.IndexRule;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Measure;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Stream;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.TagFamilySpec;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.TagSpec;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.TagType;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.TopNAggregation;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.FieldSpec;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.FieldType;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.CompressionMethod;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.EncodingMethod;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.banyandb.v1.client.BanyanDBClient;
import org.apache.skywalking.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.banyandb.v1.client.metadata.Duration;
import org.apache.skywalking.banyandb.v1.client.metadata.MetadataCache;
import org.apache.skywalking.banyandb.v1.client.metadata.ResourceExist;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntList;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;

@Slf4j
public enum MetadataRegistry {
    INSTANCE;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Map<String, Schema> registry = new HashMap<>();

    private Map<String, GroupSetting> specificGroupSettings = new HashMap<>();

    public StreamModel registerStreamModel(Model model, BanyanDBStorageConfig config, ConfigService configService) {
        final SchemaMetadata schemaMetadata = parseMetadata(model, config, configService);
        Schema.SchemaBuilder schemaBuilder = Schema.builder().metadata(schemaMetadata);
        Map<String, ModelColumn> modelColumnMap = model.getColumns().stream()
                .collect(Collectors.toMap(modelColumn -> modelColumn.getColumnName().getStorageName(), Function.identity()));
        // parse and set sharding keys
        List<String> shardingColumns = parseEntityNames(modelColumnMap);
        if (shardingColumns.isEmpty()) {
            throw new IllegalStateException("sharding keys of model[stream." + model.getName() + "] must not be empty");
        }
        // parse tag metadata
        // this can be used to build both
        // 1) a list of TagFamilySpec,
        // 2) a list of IndexRule,
        List<TagMetadata> tags = parseTagMetadata(model, schemaBuilder, shardingColumns, schemaMetadata.group);
        List<TagFamilySpec> tagFamilySpecs = schemaMetadata.extractTagFamilySpec(tags, false);
        // iterate over tagFamilySpecs to save tag names
        for (final TagFamilySpec tagFamilySpec : tagFamilySpecs) {
            for (final TagSpec tagSpec : tagFamilySpec.getTagsList()) {
                schemaBuilder.tag(tagSpec.getName());
            }
        }
        String timestampColumn4Stream = model.getBanyanDBModelExtension().getTimestampColumn();
        if (StringUtil.isBlank(timestampColumn4Stream)) {
            throw new IllegalStateException(
                    "Model[stream." + model.getName() + "] miss defined @BanyanDB.TimestampColumn");
        }
        schemaBuilder.timestampColumn4Stream(timestampColumn4Stream);
        List<IndexRule> indexRules = tags.stream()
                .map(TagMetadata::getIndexRule)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        final Stream.Builder builder = Stream.newBuilder();
        builder.setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(schemaMetadata.getGroup())
                                .setName(schemaMetadata.name()));
        builder.setEntity(BanyandbDatabase.Entity.newBuilder().addAllTagNames(shardingColumns));
        builder.addAllTagFamilies(tagFamilySpecs);

        //builder.addIndexes(indexRules);
        registry.put(schemaMetadata.name(), schemaBuilder.build());
        return new StreamModel(builder.build(), indexRules);
    }

    public MeasureModel registerMeasureModel(Model model, BanyanDBStorageConfig config, ConfigService configService) throws StorageException {
        final SchemaMetadata schemaMetadata = parseMetadata(model, config, configService);
        Schema.SchemaBuilder schemaBuilder = Schema.builder().metadata(schemaMetadata);
        Map<String, ModelColumn> modelColumnMap = model.getColumns().stream()
                .collect(Collectors.toMap(modelColumn -> modelColumn.getColumnName().getStorageName(), Function.identity()));
        // parse and set sharding keys
        List<String> shardingColumns = parseEntityNames(modelColumnMap);
        if (shardingColumns.isEmpty()) {
            throw new StorageException("model " + model.getName() + " doesn't contain series id");
        }
        // parse tag metadata
        // this can be used to build both
        // 1) a list of TagFamilySpec,
        // 2) a list of IndexRule,
        MeasureMetadata tagsAndFields = parseTagAndFieldMetadata(model, schemaBuilder, shardingColumns, schemaMetadata.group);
        List<TagFamilySpec> tagFamilySpecs = schemaMetadata.extractTagFamilySpec(tagsAndFields.tags, model.getBanyanDBModelExtension().isStoreIDTag());
        // iterate over tagFamilySpecs to save tag names
        for (final TagFamilySpec tagFamilySpec : tagFamilySpecs) {
            for (final TagSpec tagSpec : tagFamilySpec.getTagsList()) {
                schemaBuilder.tag(tagSpec.getName());
            }
        }
        List<IndexRule> indexRules = tagsAndFields.tags.stream()
                .map(TagMetadata::getIndexRule)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (model.getBanyanDBModelExtension().isStoreIDTag()) {
            indexRules.add(indexRule(schemaMetadata.group, BanyanDBConverter.ID));
           // indexRules.add(IndexRule.create(BanyanDBConverter.ID, IndexRule.IndexType.INVERTED));
        }

        final Measure.Builder builder = Measure.newBuilder();
        builder.setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(schemaMetadata.getGroup())
                                .setName(schemaMetadata.name()));
        builder.setInterval(downSamplingDuration(model.getDownsampling()).format());
        builder.setEntity(BanyandbDatabase.Entity.newBuilder().addAllTagNames(shardingColumns));
        builder.addAllTagFamilies(tagFamilySpecs);
//        if (!indexRules.isEmpty()) {
//            builder.addIndexes(indexRules);
//        }
        // parse and set field
        for (BanyandbDatabase.FieldSpec field : tagsAndFields.fields) {
            builder.addFields(field);
            schemaBuilder.field(field.getName());
        }
        // parse TopN
        schemaBuilder.topNSpec(parseTopNSpec(model, schemaMetadata.name()));

        registry.put(schemaMetadata.name(), schemaBuilder.build());
        return new MeasureModel(builder.build(), indexRules);
    }

    private TopNSpec parseTopNSpec(final Model model, final String measureName)
            throws StorageException {
        if (model.getBanyanDBModelExtension().getTopN() == null) {
            return null;
        }

        final Optional<ValueColumnMetadata.ValueColumn> valueColumnOpt = ValueColumnMetadata.INSTANCE.readValueColumnDefinition(model.getName());
        if (valueColumnOpt.isEmpty() || valueColumnOpt.get().getDataType() != Column.ValueDataType.COMMON_VALUE) {
            // skip non-single valued metrics
            return null;
        }

        if (CollectionUtils.isEmpty(model.getBanyanDBModelExtension().getTopN().getGroupByTagNames())) {
            throw new StorageException("invalid groupBy tags: " + model.getBanyanDBModelExtension().getTopN().getGroupByTagNames());
        }
        return TopNSpec.builder()
                .name(measureName + "_topn")
                .lruSize(model.getBanyanDBModelExtension().getTopN().getLruSize())
                .countersNumber(model.getBanyanDBModelExtension().getTopN().getCountersNumber())
                .fieldName(valueColumnOpt.get().getValueCName())
                .groupByTagNames(model.getBanyanDBModelExtension().getTopN().getGroupByTagNames())
                .sort(BanyandbModel.Sort.SORT_UNSPECIFIED) // include both TopN and BottomN
                .build();
    }

    public Schema findMetadata(final Model model) {
        if (model.isRecord()) {
            return findRecordMetadata(model.getName());
        }
        return findMetadata(model.getName(), model.getDownsampling());
    }

    public Schema findRecordMetadata(final String recordModelName) {
        final Schema s = this.registry.get(recordModelName);
        if (s == null) {
            return null;
        }
        // impose sanity check
        if (s.getMetadata().getKind() != Kind.STREAM) {
            throw new IllegalArgumentException(recordModelName + "is not a record");
        }
        return s;
    }

    static DownSampling deriveFromStep(Step step) {
        switch (step) {
            case DAY:
                return DownSampling.Day;
            case HOUR:
                return DownSampling.Hour;
            case SECOND:
                return DownSampling.Second;
            default:
                return DownSampling.Minute;
        }
    }

    public Schema findMetadata(final String modelName, Step step) {
        return findMetadata(modelName, deriveFromStep(step));
    }

    /**
     * Find metadata with down-sampling
     */
    public Schema findMetadata(final String modelName, DownSampling downSampling) {
        return this.registry.get(SchemaMetadata.formatName(modelName, downSampling));
    }

    private FieldSpec parseFieldSpec(ModelColumn modelColumn) {
        String colName = modelColumn.getColumnName().getStorageName();
        if (String.class.equals(modelColumn.getType())) {
            return FieldSpec.newBuilder().setName(colName)
                            .setFieldType(FieldType.FIELD_TYPE_STRING)
                            .setCompressionMethod(CompressionMethod.COMPRESSION_METHOD_ZSTD)
                            .build();
        } else if (long.class.equals(modelColumn.getType()) || int.class.equals(modelColumn.getType())) {
            return FieldSpec.newBuilder().setName(colName)
                            .setFieldType(FieldType.FIELD_TYPE_INT)
                            .setCompressionMethod(CompressionMethod.COMPRESSION_METHOD_ZSTD)
                            .setEncodingMethod(EncodingMethod.ENCODING_METHOD_GORILLA)
                            .build();
        } else if (StorageDataComplexObject.class.isAssignableFrom(modelColumn.getType()) || JsonObject.class.equals(modelColumn.getType())) {
            return FieldSpec.newBuilder().setName(colName)
                            .setFieldType(FieldType.FIELD_TYPE_STRING)
                            .setCompressionMethod(CompressionMethod.COMPRESSION_METHOD_ZSTD)
                            .build();
        } else if (double.class.equals(modelColumn.getType())) {
            // TODO: natively support double/float in BanyanDB
            log.warn("Double is stored as binary");
            return FieldSpec.newBuilder().setName(colName)
                            .setFieldType(FieldType.FIELD_TYPE_DATA_BINARY)
                            .setCompressionMethod(CompressionMethod.COMPRESSION_METHOD_ZSTD)
                            .build();
        } else {
            throw new UnsupportedOperationException(modelColumn.getType().getSimpleName() + " is not supported for field");
        }
    }

    Duration downSamplingDuration(DownSampling downSampling) {
        switch (downSampling) {
            case Hour:
                return Duration.ofHours(1);
            case Minute:
                return Duration.ofMinutes(1);
            case Day:
                return Duration.ofHours(24);
            default:
                throw new UnsupportedOperationException("unsupported downSampling interval");
        }
    }

    IndexRule indexRule(String group, String tagName) {
        return IndexRule.newBuilder()
                        .setMetadata(Metadata.newBuilder().setName(tagName).setGroup(group))
                        .setType(IndexRule.Type.TYPE_INVERTED).addTags(tagName).build();
        //return IndexRule.create(tagName, IndexRule.IndexType.INVERTED);
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

    /**
     * Parse tags' metadata for {@link Stream}
     * Every field of a class is registered as a {@link org.apache.skywalking.banyandb.model.v1.BanyandbModel.Tag}
     * regardless of its dataType.
     *
     * @since 9.4.0 Skip {@link Record#TIME_BUCKET}
     */
    List<TagMetadata> parseTagMetadata(Model model, Schema.SchemaBuilder builder, List<String> shardingColumns, String group) {
        List<TagMetadata> tagMetadataList = new ArrayList<>();
        for (final ModelColumn col : model.getColumns()) {
            final String columnStorageName = col.getColumnName().getStorageName();
            if (columnStorageName.equals(Record.TIME_BUCKET)) {
                continue;
            }
            final TagSpec tagSpec = parseTagSpec(col);
            builder.spec(columnStorageName, new ColumnSpec(ColumnType.TAG, col.getType()));
            String colName = col.getColumnName().getStorageName();
            if (!shardingColumns.contains(colName) && col.getBanyanDBExtension().shouldIndex()) {
                tagMetadataList.add(new TagMetadata(indexRule(group, tagSpec.getName()), tagSpec));
            } else {
                tagMetadataList.add(new TagMetadata(null, tagSpec));
            }
        }

        return tagMetadataList;
    }

    @Builder
    private static class MeasureMetadata {
        @Singular
        private final List<TagMetadata> tags;
        @Singular
        private final List<BanyandbDatabase.FieldSpec> fields;
    }

    /**
     * Parse tags and fields' metadata for {@link Measure}.
     * For field whose dataType is not {@link Column.ValueDataType#NOT_VALUE},
     * it is registered as {@link org.apache.skywalking.banyandb.measure.v1.BanyandbMeasure.DataPoint.Field}
     *
     * @since 9.4.0 Skip {@link Metrics#TIME_BUCKET}
     */
    MeasureMetadata parseTagAndFieldMetadata(Model model, Schema.SchemaBuilder builder, List<String> shardingColumns, String group) {
        // skip metric
        MeasureMetadata.MeasureMetadataBuilder result = MeasureMetadata.builder();
        for (final ModelColumn col : model.getColumns()) {
            final String columnStorageName = col.getColumnName().getStorageName();
            if (columnStorageName.equals(Metrics.TIME_BUCKET)) {
                continue;
            }
            if (col.getBanyanDBExtension().isMeasureField()) {
                builder.spec(columnStorageName, new ColumnSpec(ColumnType.FIELD, col.getType()));
                result.field(parseFieldSpec(col));
                continue;
            }
            final TagSpec tagSpec = parseTagSpec(col);
            builder.spec(columnStorageName, new ColumnSpec(ColumnType.TAG, col.getType()));
            String colName = col.getColumnName().getStorageName();
            result.tag(new TagMetadata(!shardingColumns.contains(colName) && col.getBanyanDBExtension().shouldIndex() ? indexRule(group, tagSpec.getName()) : null, tagSpec));
        }

        return result.build();
    }

    /**
     * Parse TagSpec from {@link ModelColumn}
     *
     * @param modelColumn the column in the model to be parsed
     * @return a typed tag spec
     */
    @Nonnull
    private TagSpec parseTagSpec(ModelColumn modelColumn) {
        final Class<?> clazz = modelColumn.getType();
        final String colName = modelColumn.getColumnName().getStorageName();
        TagSpec.Builder tagSpec = TagSpec.newBuilder().setName(colName);
        if (String.class.equals(clazz) || StorageDataComplexObject.class.isAssignableFrom(clazz) || JsonObject.class.equals(clazz)) {
            tagSpec = tagSpec.setType(TagType.TAG_TYPE_STRING);
        } else if (int.class.equals(clazz) || long.class.equals(clazz)) {
            tagSpec = tagSpec.setType(TagType.TAG_TYPE_INT);
        } else if (byte[].class.equals(clazz)) {
            tagSpec = tagSpec.setType(TagType.TAG_TYPE_DATA_BINARY);
        } else if (clazz.isEnum()) {
            tagSpec = tagSpec.setType(TagType.TAG_TYPE_INT);
        } else if (double.class.equals(clazz) || Double.class.equals(clazz)) {
            // serialize double as binary
            tagSpec = tagSpec.setType(TagType.TAG_TYPE_DATA_BINARY);
        } else if (IntList.class.isAssignableFrom(clazz)) {
            tagSpec = tagSpec.setType(TagType.TAG_TYPE_INT_ARRAY);
        } else if (List.class.isAssignableFrom(clazz)) { // handle exceptions
            ParameterizedType t = (ParameterizedType) modelColumn.getGenericType();
            if (String.class.equals(t.getActualTypeArguments()[0])) {
                tagSpec = tagSpec.setType(TagType.TAG_TYPE_STRING_ARRAY);
            }
        } else {
            throw new IllegalStateException("type " + modelColumn.getType().toString() + " is not supported");
        }

        if (modelColumn.isIndexOnly()) {
            tagSpec.setIndexedOnly(true);
        }
        return tagSpec.build();
    }

    public void initializeIntervals(String specificGroupSettingsStr) {
        if (StringUtil.isBlank(specificGroupSettingsStr)) {
            return;
        }
        try {
            specificGroupSettings = MAPPER.readerFor(new TypeReference<Map<String, GroupSetting>>() {
            }).readValue(specificGroupSettingsStr);
        } catch (IOException ioEx) {
            log.warn("fail to parse specificGroupSettings", ioEx);
        }
    }

    public SchemaMetadata parseMetadata(Model model, BanyanDBStorageConfig config, ConfigService configService) {
        int segmentIntervalDays = config.getSegmentIntervalDays();
        if (model.isSuperDataset()) {
            segmentIntervalDays = config.getSuperDatasetSegmentIntervalDays();
        }
        String group;
        int metricShardNum = config.getMetricsShardsNumber();
        if (model.isRecord()) { // stream
            group = "stream-default";
            if (model.isSuperDataset()) {
                // for superDataset, we should use separate group
                group = "stream-" + model.getName();
            }
        } else if (model.getDownsampling() == DownSampling.Minute && model.isTimeRelativeID()) { // measure
            group = "measure-minute";
            // apply super dataset's settings to measure-minute
            segmentIntervalDays = config.getSuperDatasetSegmentIntervalDays();
            metricShardNum = metricShardNum * config.getSuperDatasetShardsFactor();
        } else {
            // Solution: 2 * TTL < T * (1 + 0.8)
            // e.g. if TTL=7, T=8: a new block/segment will be created at 14.4 days,
            // while the first block has been deleted at 2*TTL
            final int intervalDays = Double.valueOf(Math.ceil(configService.getMetricsDataTTL() * 2.0 / 1.8)).intValue();
            return new SchemaMetadata("measure-default", model.getName(), Kind.MEASURE,
                    model.getDownsampling(),
                    config.getMetricsShardsNumber(),
                    intervalDays, // use 10-day/240-hour strategy
                    configService.getMetricsDataTTL());
        }

        GroupSetting groupSetting = this.specificGroupSettings.get(group);
        if (groupSetting != null) {
            segmentIntervalDays = groupSetting.getSegmentIntervalDays();
        }
        if (model.isRecord()) {
            return new SchemaMetadata(group,
                    model.getName(),
                    Kind.STREAM,
                    model.getDownsampling(),
                    config.getRecordShardsNumber() *
                            (model.isSuperDataset() ? config.getSuperDatasetShardsFactor() : 1),
                    segmentIntervalDays,
                    configService.getRecordDataTTL()
            );
        }
        // FIX: address issue #10104
        return new SchemaMetadata(group, model.getName(), Kind.MEASURE,
                model.getDownsampling(),
                metricShardNum,
                segmentIntervalDays,
                configService.getMetricsDataTTL());
    }

    @RequiredArgsConstructor
    @Data
    @ToString
    public static class SchemaMetadata {
        private final String group;
        /**
         * name of the {@link Model}
         */
        private final String modelName;
        private final Kind kind;
        /**
         * down-sampling of the {@link Model}
         */
        private final DownSampling downSampling;
        private final int shard;
        private final int segmentIntervalDays;
        private final int ttlDays;

        /**
         * Format the entity name for BanyanDB
         *
         * @param modelName    name of the model
         * @param downSampling not used if it is {@link DownSampling#None}
         * @return entity (e.g. measure, stream) name
         */
        static String formatName(String modelName, DownSampling downSampling) {
            if (downSampling == null || downSampling == DownSampling.None) {
                return modelName;
            }
            return modelName + "_" + downSampling.getName();
        }

        public Optional<Object> findRemoteSchema(BanyanDBClient client) throws BanyanDBException {
            try {
                switch (kind) {
                    case STREAM:
                        return Optional.ofNullable(client.findStream(this.group, this.name()));
                    case MEASURE:
                        return Optional.ofNullable(client.findMeasure(this.group, this.name()));
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

        public MetadataCache.EntityMetadata updateRemoteSchema(BanyanDBClient client) throws BanyanDBException {
            switch (kind) {
                case STREAM:
                    return client.updateStreamMetadataCacheFromSever(this.group, this.name());
                case MEASURE:
                    return client.updateMeasureMetadataCacheFromSever(this.group, this.name());
                default:
                    throw new IllegalStateException("should not reach here");
            }
        }

        private List<TagFamilySpec> extractTagFamilySpec(List<TagMetadata> tagMetadataList, boolean shouldAddID) {
            final String indexFamily = SchemaMetadata.this.indexFamily();
            final String nonIndexFamily = SchemaMetadata.this.nonIndexFamily();
            Map<String, List<TagMetadata>> tagMetadataMap = tagMetadataList.stream()
                    .collect(Collectors.groupingBy(tagMetadata -> tagMetadata.isIndex() ? indexFamily : nonIndexFamily));

            final List<TagFamilySpec> tagFamilySpecs = new ArrayList<>(tagMetadataMap.size());
            for (final Map.Entry<String, List<TagMetadata>> entry : tagMetadataMap.entrySet()) {
                final TagFamilySpec.Builder b = TagFamilySpec.newBuilder();
                b.setName(entry.getKey());
                b.addAllTags(entry.getValue().stream().map(TagMetadata::getTagSpec).collect(Collectors.toList()));
                if (shouldAddID && indexFamily.equals(entry.getKey())) {
                    b.addTags(TagSpec.newBuilder().setType(TagType.TAG_TYPE_STRING).setName(BanyanDBConverter.ID));
                }
                tagFamilySpecs.add(b.build());
            }

            return tagFamilySpecs;
        }

        public boolean checkResourceExistence(BanyanDBClient client) throws BanyanDBException {
            ResourceExist resourceExist;
            Group.Builder gBuilder
                = Group.newBuilder()
                       .setMetadata(Metadata.newBuilder().setName(this.group))
                       .setResourceOpts(ResourceOpts.newBuilder()
                                                    .setShardNum(this.shard)
                                                    .setSegmentInterval(
                                                        IntervalRule.newBuilder()
                                                                    .setUnit(
                                                                        IntervalRule.Unit.UNIT_DAY)
                                                                    .setNum(
                                                                        this.segmentIntervalDays))
                                                    .setTtl(
                                                        IntervalRule.newBuilder()
                                                                    .setUnit(
                                                                        IntervalRule.Unit.UNIT_DAY)
                                                                    .setNum(
                                                                        this.ttlDays)));
            switch (kind) {
                case STREAM:
                    resourceExist = client.existStream(this.group, this.name());
                    if (!resourceExist.hasGroup()) {
                        try {
                            Group g = client.define(gBuilder.setCatalog(Catalog.CATALOG_STREAM).build());
                            if (g != null) {
                                log.info("group {} created", g.getMetadata().getName());
                            }
                        } catch (BanyanDBException ex) {
                            if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                                log.info("group {} already created by another OAP node", this.group);
                            } else {
                                throw ex;
                            }
                        }
                    }
                    return resourceExist.hasResource();
                case MEASURE:
                    resourceExist = client.existMeasure(this.group, this.name());
                    try {
                        if (!resourceExist.hasGroup()) {
                            Group g = client.define(gBuilder.setCatalog(Catalog.CATALOG_MEASURE).build());
//                                Group.create(this.group, Catalog.MEASURE, this.shard,
//                                                                 IntervalRule.create(
//                                                                     IntervalRule.Unit.DAY, this.segmentIntervalDays),
//                                                                 IntervalRule.create(
//                                                                     IntervalRule.Unit.DAY, this.ttlDays)
//                            ));
                            if (g != null) {
                                log.info("group {} created", g.getMetadata().getName());
                            }
                        }
                    } catch (BanyanDBException ex) {
                        if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                            log.info("group {} already created by another OAP node", this.group);
                        } else {
                            throw ex;
                        }
                    }
                    return resourceExist.hasResource();
                default:
                    throw new IllegalStateException("should not reach here");
            }
        }

        /**
         * @return name of the Stream/Measure in the BanyanDB
         */
        public String name() {
            if (this.kind == Kind.MEASURE) {
                return formatName(this.modelName, this.downSampling);
            }
            return this.modelName;
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
                case STREAM:
                    return "storage-only";
                default:
                    throw new IllegalStateException("should not reach here");
            }
        }
    }

    public enum Kind {
        MEASURE, STREAM;
    }

    @RequiredArgsConstructor
    @Getter
    private static class TagMetadata {
        private final IndexRule indexRule;
        private final TagSpec tagSpec;

        boolean isIndex() {
            return this.indexRule != null;
        }
    }

    @Builder
    @EqualsAndHashCode
    @ToString
    public static class Schema {
        @Getter
        private final SchemaMetadata metadata;
        @Singular
        private final Map<String, ColumnSpec> specs;

        @Getter
        @Singular
        private final Set<String> tags;

        @Getter
        @Singular
        private final Set<String> fields;

        @Getter
        private final String timestampColumn4Stream;

        @Getter
        @Nullable
        private final TopNSpec topNSpec;

        public ColumnSpec getSpec(String columnName) {
            return this.specs.get(columnName);
        }

        public void installTopNAggregation(BanyanDBClient client) throws BanyanDBException {
            if (this.getTopNSpec() == null) {
                if (this.metadata.kind == Kind.MEASURE) {
                    log.debug("skip null TopN Schema for [{}]", metadata.getModelName());
                }
                return;
            }
            TopNAggregation.Builder builder
                = TopNAggregation.newBuilder()
                                 .setMetadata(Metadata.newBuilder()
                                                      .setGroup(getMetadata().getGroup())
                                                      .setName(this.getTopNSpec().getName()))

                                 .setSourceMeasure(Metadata.newBuilder()
                                                           .setGroup(getMetadata().getGroup())
                                                           .setName(getMetadata().name()))
                                 .setFieldValueSort(this.getTopNSpec().getSort())
                                 .setFieldName(this.getTopNSpec().getFieldName())
                                 .addAllGroupByTagNames(this.getTopNSpec().getGroupByTagNames())
                                 .setCountersNumber(this.getTopNSpec().getCountersNumber())
                                 .setLruSize(this.getTopNSpec().getLruSize());
            client.define(builder.build());
//            client.define(TopNAggregation.create(getMetadata().getGroup(), this.getTopNSpec().getName())
//                    .setSourceMeasureName(getMetadata().name())
//                    .setFieldValueSort(this.getTopNSpec().getSort())
//                    .setFieldName(this.getTopNSpec().getFieldName())
//                    .setGroupByTagNames(this.getTopNSpec().getGroupByTagNames())
//                    .setCountersNumber(this.getTopNSpec().getCountersNumber())
//                    .setLruSize(this.getTopNSpec().getLruSize())
//                    .build());
            log.info("installed TopN schema for measure {}", getMetadata().name());
        }
    }

    @Builder
    @EqualsAndHashCode
    @Getter
    @ToString
    public static class TopNSpec {
        private final String name;
        @Singular
        private final List<String> groupByTagNames;
        private final String fieldName;
        private final BanyandbModel.Sort sort;
        private final int lruSize;
        private final int countersNumber;
    }

    @RequiredArgsConstructor
    @Getter
    @ToString
    public static class ColumnSpec {
        private final ColumnType columnType;
        private final Class<?> columnClass;
    }

    public enum ColumnType {
        TAG, FIELD;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class GroupSetting {
        private int segmentIntervalDays;
    }
}
