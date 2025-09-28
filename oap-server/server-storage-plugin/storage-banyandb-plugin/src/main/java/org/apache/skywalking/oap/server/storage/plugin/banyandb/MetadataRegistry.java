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
import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.function.BiFunction;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Singular;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon.Metadata;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Property;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.CompressionMethod;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.EncodingMethod;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.FieldSpec;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.FieldType;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.IndexRule;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Measure;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Stream;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Trace;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.TagFamilySpec;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.TagSpec;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.TraceTagSpec;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.TagType;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.TopNAggregation;
import org.apache.skywalking.banyandb.v1.client.AbstractCriteria;
import org.apache.skywalking.banyandb.v1.client.And;
import org.apache.skywalking.banyandb.v1.client.PairQueryCondition;
import org.apache.skywalking.banyandb.v1.client.metadata.Duration;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntList;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.model.BanyanDBTrace;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.config.DownSamplingConfigService;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.model.BanyanDBModelExtension.TraceIndexRule;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.IndexRule.Type.TYPE_INVERTED;
import static org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.IndexRule.Type.TYPE_SKIPPING;
import static org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.IndexRule.Type.TYPE_TREE;
import static org.apache.skywalking.oap.server.core.analysis.metrics.Metrics.ID;

@Slf4j
public enum MetadataRegistry {
    INSTANCE;

    private final Map<String, Schema> registry = new HashMap<>();

    public StreamModel registerStreamModel(Model model, BanyanDBStorageConfig config) {
        final SchemaMetadata schemaMetadata = parseMetadata(model, config, null);
        Schema.SchemaBuilder schemaBuilder = Schema.builder().metadata(schemaMetadata);
        Map<String, ModelColumn> modelColumnMap = model.getColumns().stream()
                .collect(Collectors.toMap(modelColumn -> modelColumn.getColumnName().getStorageName(), Function.identity()));
        // parse and set seriesIDs
        List<String> seriesIDColumns = parseEntityNames(modelColumnMap);
        if (seriesIDColumns.isEmpty()) {
            throw new IllegalStateException("seriesID of model[stream." + model.getName() + "] must not be empty");
        }
        // parse tag metadata
        // this can be used to build both
        // 1) a list of TagFamilySpec,
        // 2) a list of IndexRule,
        List<TagMetadata> tags = parseTagMetadata(model, schemaBuilder, seriesIDColumns, schemaMetadata.group);
        List<TagFamilySpec> tagFamilySpecs = schemaMetadata.extractTagFamilySpec(tags);
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
        schemaBuilder.timestampColumn(timestampColumn4Stream);
        List<IndexRule> indexRules = tags.stream()
                .map(TagMetadata::getIndexRule)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        final Stream.Builder builder = Stream.newBuilder();
        builder.setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(schemaMetadata.getGroup())
                .setName(schemaMetadata.name()));
        builder.setEntity(BanyandbDatabase.Entity.newBuilder().addAllTagNames(seriesIDColumns));
        builder.addAllTagFamilies(tagFamilySpecs);

        registry.put(schemaMetadata.name(), schemaBuilder.build());
        return new StreamModel(builder.build(), indexRules);
    }

    public MeasureModel registerMeasureModel(Model model, BanyanDBStorageConfig config, DownSamplingConfigService configService) throws StorageException {
        final SchemaMetadata schemaMetadata = parseMetadata(model, config, configService);
        Schema.SchemaBuilder schemaBuilder = Schema.builder().metadata(schemaMetadata);
        Map<String, ModelColumn> modelColumnMap = model.getColumns().stream()
                .collect(Collectors.toMap(modelColumn -> modelColumn.getColumnName().getStorageName(), Function.identity()));
        // parse and set seriesIDs
        List<String> seriesIDColumns = parseEntityNames(modelColumnMap);
        List<String> shardingKeyColumns = parseShardingKeyNames(modelColumnMap);
        boolean isIndexMode = model.getBanyanDBModelExtension().isIndexMode();
        if (isIndexMode) {
            // in index mode, seriesID must contain ID
            seriesIDColumns.add(ID);
        }
        if (seriesIDColumns.isEmpty()) {
            throw new StorageException("model " + model.getName() + " doesn't contain series id");
        }
        // parse tag metadata
        // this can be used to build both
        // 1) a list of TagFamilySpec,
        // 2) a list of IndexRule,
        MeasureMetadata tagsAndFields = parseTagAndFieldMetadata(
            model, schemaBuilder, seriesIDColumns, schemaMetadata.group,
            isIndexMode
        );
        List<TagFamilySpec> tagFamilySpecs = schemaMetadata.extractTagFamilySpec(tagsAndFields.tags);
        // iterate over tagFamilySpecs to save tag names
        Set<String> tags = tagFamilySpecs.stream()
                .flatMap(tagFamilySpec -> tagFamilySpec.getTagsList().stream())

                .map(TagSpec::getName)
                .collect(Collectors.toSet());
        schemaBuilder.tags(tags);
        List<IndexRule> indexRules = tagsAndFields.tags.stream()
                .map(TagMetadata::getIndexRule)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        final Measure.Builder builder = Measure.newBuilder();
        builder.setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(schemaMetadata.getGroup())
                .setName(schemaMetadata.name()));
        builder.setInterval(downSamplingDuration(model.getDownsampling()).format());
        builder.setEntity(BanyandbDatabase.Entity.newBuilder().addAllTagNames(seriesIDColumns));
        if (CollectionUtils.isNotEmpty(shardingKeyColumns)) {
            builder.setShardingKey(BanyandbDatabase.ShardingKey.newBuilder().addAllTagNames(shardingKeyColumns));
        }
        builder.addAllTagFamilies(tagFamilySpecs);
        if (model.getBanyanDBModelExtension().isIndexMode()) {
            builder.setIndexMode(true);
            if (!tagsAndFields.fields.isEmpty()) {
                throw new StorageException("index mode is enabled, but fields are defined");
            }
        }
        // parse and set field
        for (BanyandbDatabase.FieldSpec field : tagsAndFields.fields) {
            builder.addFields(field);
            schemaBuilder.field(field.getName());
        }
        // parse TopN
        schemaBuilder.topNSpecs(parseTopNSpecs(
            model, schemaMetadata.group, schemaMetadata.name(),
            config.getTopNConfigs().get(model.getName()),
            tags
        ));
        registry.put(schemaMetadata.name(), schemaBuilder.build());
        return new MeasureModel(builder.build(), indexRules);
    }

    public PropertyModel registerPropertyModel(Model model, BanyanDBStorageConfig config) {
        final SchemaMetadata schemaMetadata = parseMetadata(model, config, null);
        Schema.SchemaBuilder schemaBuilder = Schema.builder().metadata(schemaMetadata);
        List<TagMetadata> tags = parseTagMetadata(model, schemaBuilder, Collections.emptyList(), schemaMetadata.group);
        final Property.Builder builder = Property.newBuilder();
        builder.setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(schemaMetadata.getGroup())
                .setName(schemaMetadata.name()));
        for (TagMetadata tag : tags) {
            builder.addTags(tag.getTagSpec());
        }
        registry.put(schemaMetadata.name(), schemaBuilder.build());
        return new PropertyModel(builder.build());
    }

    public TraceModel registerTraceModel(Model model, BanyanDBStorageConfig config) {
        final SchemaMetadata schemaMetadata = parseMetadata(model, config, null);
        Schema.SchemaBuilder schemaBuilder = Schema.builder().metadata(schemaMetadata);
        String timestampColumn = model.getBanyanDBModelExtension().getTimestampColumn();
        schemaBuilder.timestampColumn(timestampColumn);
        final Trace.Builder builder = Trace.newBuilder();
        builder.setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(schemaMetadata.getGroup())
                                                   .setName(schemaMetadata.name()));
        builder.setTraceIdTagName(model.getBanyanDBModelExtension().getTraceIdColumn());
        builder.setTimestampTagName(timestampColumn);
        for (final ModelColumn col : model.getColumns()) {
            final String columnStorageName = col.getColumnName().getStorageName();
            // skip storage only column, since they are not supposed to be queried
            if (col.isStorageOnly()) {
                continue;
            }
            if (columnStorageName.equals(Record.TIME_BUCKET)) {
                continue;
            }
            if (!col.getBanyanDBExtension().shouldIndex()) {
                continue;
            }
            final TagSpec tagSpec = parseTagSpec(col);
            final TraceTagSpec.Builder traceTagSpec = TraceTagSpec.newBuilder();
            traceTagSpec.setName(tagSpec.getName());
            traceTagSpec.setType(tagSpec.getType());
            // in trace, the timestamp column must be TAG_TYPE_TIMESTAMP
            if (columnStorageName.equals(timestampColumn)) {
                traceTagSpec.setType(TagType.TAG_TYPE_TIMESTAMP);
            }
            builder.addTags(traceTagSpec.build());
            schemaBuilder.spec(columnStorageName, new ColumnSpec(ColumnType.TAG, col.getType()));
            schemaBuilder.tag(tagSpec.getName());
        }

        List<TraceIndexRule> traceIndexRules = model.getBanyanDBModelExtension().getTraceIndexRules();
        List<IndexRule> indexRules = null;
        if (!BanyanDBTrace.MergeTable.class.isAssignableFrom(model.getStreamClass())) {
            if (CollectionUtils.isEmpty(traceIndexRules)) {
                throw new IllegalStateException(
                    "Model[trace." + model.getName() + "] miss defined @BanyanDB.Trace.IndexRuleColumns");
            }

            indexRules = new ArrayList<>(traceIndexRules.size());
            for (TraceIndexRule traceIndexRule : traceIndexRules) {
                IndexRule.Builder indexRule = IndexRule.newBuilder()
                                                       .setType(TYPE_TREE)
                                                       .setNoSort(false)
                                                       .setMetadata(Metadata.newBuilder()
                                                                            .setName(traceIndexRule.getName())
                                                                            .setGroup(schemaMetadata.getGroup()))
                                                       .addAllTags(List.of(traceIndexRule.getColumns()))
                                                       .addTags(
                                                           traceIndexRule.getOrderByColumn()); // make sure the orderBy column is the last one
                indexRules.add(indexRule.build());
            }
        }

        registry.put(schemaMetadata.name(), schemaBuilder.build());
        return new TraceModel(builder.build(), indexRules);
    }

    private Map<ImmutableSet<String>, TopNAggregation> parseTopNSpecs(final Model model,
                                                                      final String group,
                                                                      final String measureName,
                                                                      final Map<String, BanyanDBStorageConfig.TopN> topNConfig,
                                                                      final Set<String> tags) throws StorageException {
        if (topNConfig == null) {
            return null;
        }

        final Optional<ValueColumnMetadata.ValueColumn> valueColumnOpt = ValueColumnMetadata.INSTANCE.readValueColumnDefinition(model.getName());
        if (valueColumnOpt.isEmpty() || valueColumnOpt.get().getDataType() != Column.ValueDataType.COMMON_VALUE) {
            // skip non-single valued metrics
            return null;
        }
        Map<ImmutableSet<String>, TopNAggregation> topNAggregations = new HashMap<>();
        topNConfig.forEach((name, topN) -> {
            ImmutableSet<String> key = ImmutableSet.of();
            Set<String> queryConditions = new HashSet<>();
            TopNAggregation.Builder topNAggregation = TopNAggregation.newBuilder()
                                                                     .setMetadata(
                                                                         Metadata.newBuilder().setGroup(group).setName(name))
                                                                     .setSourceMeasure(Metadata.newBuilder().setGroup(group).setName(measureName))
                                                                     .setFieldValueSort(topN.getSort().getBanyandbSort())
                                                                     .setFieldName(valueColumnOpt.get().getValueCName())
                                                                     .setCountersNumber(topN.getCountersNumber());
            if (topN.getGroupByTagNames() != null) {
                queryConditions.addAll(topN.getGroupByTagNames());
                //check tags
                topN.getGroupByTagNames().forEach(tag -> {
                    if (!tags.contains(tag)) {
                        throw new IllegalArgumentException(
                            "In file [bydb-topn.yml], TopN rule " + topN.getName() + "'s groupByTagName [" + tag + "] is not defined in metric " + model.getName());
                    }
                });
                topNAggregation.addAllGroupByTagNames(topN.getGroupByTagNames());
            }

            switch (model.getDownsampling()) {
                case Minute:
                    topNAggregation.setLruSize(topN.getLruSizeMinute());
                    break;
                case Hour:
                case Day:
                    topNAggregation.setLruSize(topN.getLruSizeHourDay());
                    break;
                default:
                    throw new UnsupportedOperationException("unsupported downsampling: " + model.getDownsampling());
            }

            if (CollectionUtils.isNotEmpty(topN.getExcludes())) {
                AbstractCriteria criteria;
                List<AbstractCriteria> conditions = new ArrayList<>(topN.getExcludes().size());
                for (KeyValue keyValue : topN.getExcludes()) {
                    conditions.add(PairQueryCondition.StringQueryCondition.ne(keyValue.getKey(), keyValue.getValue()));
                    queryConditions.remove(keyValue.getKey());
                    queryConditions.add(keyValue.getKey() + "!=" + keyValue.getValue());
                }
                if (conditions.size() == 1) {
                    criteria = conditions.get(0);
                } else {
                    criteria = conditions.subList(2, conditions.size()).stream().reduce(
                        And.create(conditions.get(0), conditions.get(1)),
                        (BiFunction<AbstractCriteria, AbstractCriteria, AbstractCriteria>) And::create,
                        And::create);
                }
                topNAggregation.setCriteria(criteria.build());
            }
            key = ImmutableSet.copyOf(queryConditions);
            if (topNAggregations.containsKey(key)) {
                throw new IllegalArgumentException("In file [bydb-topn.yml], TopN rule " + topN.getName() + "'s groupByTagNames and excludes " + key + " already exist in the same metric " + model.getName());
            }
            topNAggregations.put(key, topNAggregation.build());
        });
        return topNAggregations;
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

    public Schema findMetricMetadata(final String modelName, Step step) {
        return findMetricMetadata(modelName, deriveFromStep(step));
    }

    /**
     * Find metadata with down-sampling
     */
    public Schema findMetricMetadata(final String modelName, DownSampling downSampling) {
        return this.registry.get(SchemaMetadata.formatName(modelName, downSampling));
    }

    public Schema findRecordMetadata(final String modelName) {
        return this.registry.get(modelName);
    }

    public Schema findManagementMetadata(final String modelName) {
        return this.registry.get(modelName);
    }

    public Schema findMetadata(final Model model) {
        if (!model.isTimeSeries()) {
            return findManagementMetadata(model.getName());
        }
        if (model.isRecord()) {
            return findRecordMetadata(model.getName());
        }
        return findMetricMetadata(model.getName(), model.getDownsampling());
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

    IndexRule indexRule(String group,
                        String tagName,
                        boolean enableSort,
                        BanyanDB.MatchQuery.AnalyzerType analyzer,
                        BanyanDB.IndexRule.IndexType type) {
        IndexRule.Builder builder = IndexRule.newBuilder()
                                             .setMetadata(Metadata.newBuilder().setName(tagName).setGroup(group))
                                             .addTags(tagName);
        // *Notice*: here is a reverse logic, if enableSort is true, then setNoSort is false
        builder.setNoSort(!enableSort);

        if (type != null) {
            switch (type) {
                case INVERTED:
                    builder.setType(TYPE_INVERTED);
                    break;
                case TREE:
                    builder.setType(TYPE_TREE);
                    break;
                case SKIPPING:
                    builder.setType(TYPE_SKIPPING);
                    break;
                default:
                    throw new UnsupportedOperationException("unsupported index type: " + type);
            }
        } else {
            builder.setType(TYPE_INVERTED);
        }

        if (analyzer != null) {
            switch (analyzer) {
                case KEYWORD:
                    builder.setAnalyzer("keyword");
                    break;
                case STANDARD:
                    builder.setAnalyzer("standard");
                    break;
                case SIMPLE:
                    builder.setAnalyzer("simple");
                    break;
                case URL:
                    builder.setAnalyzer("url");
                    break;
                default:
                    throw new UnsupportedOperationException("unsupported analyzer type: " + analyzer);
            }
        }
        return builder.build();
    }

    /**
     * Parse SeriesID from the {@link Model}
     *
     * @param modelColumnMap the mapping between column storageName and {@link ModelColumn}
     * @return a list of column names in strict order
     */
    List<String> parseEntityNames(Map<String, ModelColumn> modelColumnMap) {
        List<ModelColumn> seriesIDColumns = new ArrayList<>();
        for (final ModelColumn col : modelColumnMap.values()) {
            if (col.getBanyanDBExtension().isSeriesID()) {
                seriesIDColumns.add(col);
            }
        }
        return seriesIDColumns.stream()
                .sorted(Comparator.comparingInt(col -> col.getBanyanDBExtension().getSeriesIDIdx()))
                .map(col -> col.getColumnName().getName())
                .collect(Collectors.toList());
    }

    List<String> parseShardingKeyNames(Map<String, ModelColumn> modelColumnMap) {
        List<ModelColumn> shardingKeyColumns = new ArrayList<>();
        for (final ModelColumn col : modelColumnMap.values()) {
            if (col.getBanyanDBExtension().isShardingKey()) {
                shardingKeyColumns.add(col);
            }
        }
        return shardingKeyColumns.stream()
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
    List<TagMetadata> parseTagMetadata(Model model, Schema.SchemaBuilder builder, List<String> seriesIDColumns, String group) {
        List<TagMetadata> tagMetadataList = new ArrayList<>();
        for (final ModelColumn col : model.getColumns()) {
            final String columnStorageName = col.getColumnName().getStorageName();
            if (columnStorageName.equals(Record.TIME_BUCKET)) {
                continue;
            }
            final TagSpec tagSpec = parseTagSpec(col);
            builder.spec(columnStorageName, new ColumnSpec(ColumnType.TAG, col.getType()));
            String colName = col.getColumnName().getStorageName();
            if (col.getBanyanDBExtension().shouldIndex() && !colName.equals(model.getBanyanDBModelExtension().getTimestampColumn())) {
                if (!seriesIDColumns.contains(colName) || null != col.getBanyanDBExtension().getAnalyzer()) {
                    tagMetadataList.add(new TagMetadata(
                        indexRule(
                            group, tagSpec.getName(), col.getBanyanDBExtension().isEnableSort(),
                            col.getBanyanDBExtension().getAnalyzer(),
                            col.getBanyanDBExtension().getIndexType()
                        ), tagSpec));
                } else {
                    tagMetadataList.add(new TagMetadata(null, tagSpec));
                }
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
    MeasureMetadata parseTagAndFieldMetadata(Model model,
                                             Schema.SchemaBuilder builder,
                                             List<String> seriesIDColumns,
                                             String group,
                                             boolean shouldAddID) {
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

            if (col.getBanyanDBExtension().shouldIndex() && !colName.equals(model.getBanyanDBModelExtension().getTimestampColumn())) {
                if (!seriesIDColumns.contains(colName) || null != col.getBanyanDBExtension().getAnalyzer()) {
                    result.tag(new TagMetadata(
                        indexRule(
                            group, tagSpec.getName(), col.getBanyanDBExtension().isEnableSort(),
                            col.getBanyanDBExtension().getAnalyzer(),
                            col.getBanyanDBExtension().getIndexType()
                        ), tagSpec));
                } else {
                    result.tag(new TagMetadata(null, tagSpec));
                }
            } else {
                result.tag(new TagMetadata(null, tagSpec));
            }
        }
        // add additional ID tag
        if (shouldAddID) {
            result.tag(new TagMetadata(
                null, TagSpec.newBuilder().setType(TagType.TAG_TYPE_STRING).setName(ID).build()
            ));
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
        } else if (int.class.equals(clazz) || long.class.equals(clazz) || Integer.class.equals(clazz) || Long.class.equals(clazz)) {
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
        return tagSpec.build();
    }

    public SchemaMetadata parseMetadata(Model model, BanyanDBStorageConfig config, DownSamplingConfigService configService) {
        String namespace = config.getGlobal().getNamespace();
        if (!model.isTimeSeries()) {
            return new SchemaMetadata(
                namespace,
                BanyanDB.PropertyGroup.PROPERTY.getName(),
                model.getName(),
                Kind.PROPERTY,
                DownSampling.None,
                config.getProperty()
            );
        }
        if (model.isRecord()) {
            BanyanDB.TraceGroup traceGroup = model.getBanyanDBModelExtension().getTraceGroup();
            if (BanyanDB.TraceGroup.NONE != traceGroup) {
                // trace
                switch (traceGroup) {
                    case TRACE:
                        return new SchemaMetadata(
                            namespace,
                            BanyanDB.TraceGroup.TRACE.getName(),
                            model.getName(),
                            Kind.TRACE,
                            DownSampling.None,
                            config.getTrace()
                        );
                    case ZIPKIN_TRACE:
                        return new SchemaMetadata(
                            namespace,
                            BanyanDB.TraceGroup.ZIPKIN_TRACE.getName(),
                            model.getName(),
                            Kind.TRACE,
                            DownSampling.None,
                            config.getZipkinTrace()
                        );
                    default:
                        throw new IllegalStateException("unknown trace group " + traceGroup);
                }
            } else {
                // stream
                BanyanDB.StreamGroup streamGroup = model.getBanyanDBModelExtension().getStreamGroup();
                switch (streamGroup) {
                    case RECORDS_LOG:
                        return new SchemaMetadata(
                            namespace,
                            BanyanDB.StreamGroup.RECORDS_LOG.getName(),
                            model.getName(),
                            Kind.STREAM,
                            model.getDownsampling(),
                            config.getRecordsLog()
                        );
                    case RECORDS_BROWSER_ERROR_LOG:
                        return new SchemaMetadata(
                            namespace,
                            BanyanDB.StreamGroup.RECORDS_BROWSER_ERROR_LOG.getName(),
                            model.getName(),
                            Kind.STREAM,
                            model.getDownsampling(),
                            config.getRecordsBrowserErrorLog()
                        );
                    case RECORDS:
                        return new SchemaMetadata(
                            namespace,
                            BanyanDB.StreamGroup.RECORDS.getName(),
                            model.getName(),
                            Kind.STREAM,
                            model.getDownsampling(),
                            config.getRecordsBrowserErrorLog()
                        );
                    default:
                        throw new IllegalStateException("unknown stream group " + streamGroup);
                }
            }
        }

        if (model.getBanyanDBModelExtension().isIndexMode()) {
            return new SchemaMetadata(
                namespace,
                BanyanDB.MeasureGroup.METADATA.getName(),
                model.getName(),
                Kind.MEASURE,
                model.getDownsampling(),
                config.getMetadata()
            );
        }

        switch (model.getDownsampling()) {
            case Minute:
                return new SchemaMetadata(
                    namespace,
                    BanyanDB.MeasureGroup.METRICS_MINUTE.getName(),
                    model.getName(),
                    Kind.MEASURE,
                    model.getDownsampling(),
                    config.getMetricsMin()
                );
            case Hour:
                if (!configService.shouldToHour()) {
                    throw new UnsupportedOperationException("downsampling to hour is not supported");
                }
                return new SchemaMetadata(
                    namespace,
                    BanyanDB.MeasureGroup.METRICS_HOUR.getName(),
                    model.getName(),
                    Kind.MEASURE,
                    model.getDownsampling(),
                    config.getMetricsHour()
                );
            case Day:
                if (!configService.shouldToDay()) {
                    throw new UnsupportedOperationException("downsampling to day is not supported");
                }
                return new SchemaMetadata(
                    namespace,
                    BanyanDB.MeasureGroup.METRICS_DAY.getName(),
                    model.getName(),
                    Kind.MEASURE,
                    model.getDownsampling(),
                    config.getMetricsDay()
                );
            default:
                throw new UnsupportedOperationException("unsupported downSampling interval:" + model.getDownsampling());
        }
    }

    @Getter
    @ToString
    public static class SchemaMetadata {
        private final String namespace;
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
        private final BanyanDBStorageConfig.GroupResource resource;

        public SchemaMetadata(final String namespace,
                              final String group,
                              final String modelName,
                              final Kind kind,
                              final DownSampling downSampling,
                              final BanyanDBStorageConfig.GroupResource resource) {
            this.namespace = namespace;
            this.modelName = modelName;
            this.kind = kind;
            this.downSampling = downSampling;
            this.resource = resource;
            this.group = convertGroupName(namespace, group);

        }

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

        private List<TagFamilySpec> extractTagFamilySpec(List<TagMetadata> tagMetadataList) {
            final String indexFamily = SchemaMetadata.this.indexFamily();
            final String nonIndexFamily = SchemaMetadata.this.nonIndexFamily();
            Map<String, List<TagMetadata>> tagMetadataMap = tagMetadataList.stream()
                    .collect(Collectors.groupingBy(tagMetadata -> tagMetadata.isIndex() ? indexFamily : nonIndexFamily));

            final List<TagFamilySpec> tagFamilySpecs = new ArrayList<>(tagMetadataMap.size());
            for (final Map.Entry<String, List<TagMetadata>> entry : tagMetadataMap.entrySet()) {
                final TagFamilySpec.Builder b = TagFamilySpec.newBuilder();
                b.setName(entry.getKey());
                b.addAllTags(entry.getValue().stream().map(TagMetadata::getTagSpec).collect(Collectors.toList()));
                tagFamilySpecs.add(b.build());
            }

            return tagFamilySpecs;
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
        MEASURE, STREAM, PROPERTY, TRACE;
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
        private final String timestampColumn;

        @Getter
        @Nullable
        private final Map<ImmutableSet<String>/*groupBy tags*/, TopNAggregation> topNSpecs;

        public ColumnSpec getSpec(String columnName) {
            return this.specs.get(columnName);
        }
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

    public static String convertGroupName(String namespace, String groupName) {
        if (StringUtil.isNotEmpty(namespace)) {
            return namespace + "_" + groupName;
        } else {
            return groupName;
        }
    }
}
