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
 */

package org.apache.skywalking.oap.server.core.storage.model;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;
import org.apache.skywalking.oap.server.core.storage.annotation.SQLDatabase;
import org.apache.skywalking.oap.server.core.storage.annotation.Storage;
import org.apache.skywalking.oap.server.core.storage.annotation.SuperDataset;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * StorageModels manages all models detected by the core.
 */
@Slf4j
public class StorageModels implements IModelManager, ModelCreator, ModelManipulator {
    private final List<Model> models;
    private final HashMap<String, String> columnNameOverrideRule;
    private final List<CreatingListener> listeners;

    public StorageModels() {
        this.models = new ArrayList<>();
        this.columnNameOverrideRule = new HashMap<>();
        this.listeners = new ArrayList<>();
    }

    @Override
    public Model add(Class<?> aClass, int scopeId, Storage storage) throws StorageException {
        // Check this scope id is valid.
        DefaultScopeDefine.nameOf(scopeId);

        List<ModelColumn> modelColumns = new ArrayList<>();
        ShardingKeyChecker checker = new ShardingKeyChecker();
        SQLDatabaseModelExtension sqlDBModelExtension = new SQLDatabaseModelExtension();
        BanyanDBModelExtension banyanDBModelExtension = new BanyanDBModelExtension();
        ElasticSearchModelExtension elasticSearchModelExtension = new ElasticSearchModelExtension();
        retrieval(aClass, storage.getModelName(), modelColumns, scopeId, checker, sqlDBModelExtension, banyanDBModelExtension);
        // Add extra column for additional entities
        if (aClass.isAnnotationPresent(SQLDatabase.ExtraColumn4AdditionalEntity.class)
                || aClass.isAnnotationPresent(SQLDatabase.MultipleExtraColumn4AdditionalEntity.class)) {
            Map<String/*parent column*/, List<String>/*tables*/> extraColumns = new HashMap<>();
            if (aClass.isAnnotationPresent(SQLDatabase.MultipleExtraColumn4AdditionalEntity.class)) {
                for (SQLDatabase.ExtraColumn4AdditionalEntity extraColumn : aClass.getAnnotation(
                        SQLDatabase.MultipleExtraColumn4AdditionalEntity.class).value()) {
                    List<String> tables = extraColumns.computeIfAbsent(
                            extraColumn.parentColumn(), v -> new ArrayList<>());
                    tables.add(extraColumn.additionalTable());
                }
            } else {
                SQLDatabase.ExtraColumn4AdditionalEntity extraColumn = aClass.getAnnotation(
                        SQLDatabase.ExtraColumn4AdditionalEntity.class);
                List<String> tables = extraColumns.computeIfAbsent(extraColumn.parentColumn(), v -> new ArrayList<>());
                tables.add(extraColumn.additionalTable());
            }

            extraColumns.forEach((extraColumn, tables) -> {
                if (!addExtraColumn4AdditionalEntity(sqlDBModelExtension, modelColumns, extraColumn, tables)) {
                    throw new IllegalStateException(
                            "Model [" + storage.getModelName() + "] defined an extra column  [" + extraColumn + "]  by @SQLDatabase.ExtraColumn4AdditionalEntity, " +
                                    "but couldn't be found from the parent.");
                }
            });
        }
        //Add timestampColumn for BanyanDB
        if (aClass.isAnnotationPresent(BanyanDB.TimestampColumn.class)) {
            String timestampColumn = aClass.getAnnotation(BanyanDB.TimestampColumn.class).value();
            banyanDBModelExtension.setTimestampColumn(timestampColumn);
        }

        if (aClass.isAnnotationPresent(BanyanDB.StoreIDAsTag.class)) {
            banyanDBModelExtension.setStoreIDTag(true);
        }

        // Set routing rules for ElasticSearch
        elasticSearchModelExtension.setRouting(storage.getModelName(), modelColumns);

        checker.check(storage.getModelName());

        Model model = new Model(
            storage.getModelName(),
            modelColumns,
            scopeId,
            storage.getDownsampling(),
            isSuperDatasetModel(aClass),
            aClass,
            storage.isTimeRelativeID(),
            sqlDBModelExtension,
            banyanDBModelExtension,
            elasticSearchModelExtension
        );

        this.followColumnNameRules(model);
        models.add(model);

        for (final CreatingListener listener : listeners) {
            listener.whenCreating(model);
        }
        return model;
    }

    private boolean isSuperDatasetModel(Class<?> aClass) {
        return aClass.isAnnotationPresent(SuperDataset.class);
    }

    /**
     * CreatingListener listener could react when {@link ModelCreator#add(Class, int, Storage)} model happens. Also, the
     * added models are being notified in this add operation.
     */
    @Override
    public void addModelListener(final CreatingListener listener) throws StorageException {
        listeners.add(listener);
        for (Model model : models) {
            listener.whenCreating(model);
        }
    }

    /**
     * Read model column metadata based on the class level definition.
     */
    private void retrieval(final Class<?> clazz,
                           final String modelName,
                           final List<ModelColumn> modelColumns,
                           final int scopeId,
                           ShardingKeyChecker checker,
                           final SQLDatabaseModelExtension sqlDBModelExtension,
                           final BanyanDBModelExtension banyanDBModelExtension) {
        if (log.isDebugEnabled()) {
            log.debug("Analysis {} to generate Model.", clazz.getName());
        }

        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            if (field.isAnnotationPresent(Column.class)) {
                if (field.isAnnotationPresent(SQLDatabase.AdditionalEntity.class)) {
                    if (!Record.class.isAssignableFrom(clazz)) {
                        throw new IllegalStateException(
                                "Model [" + modelName + "] is not a Record, @SQLDatabase.AdditionalEntity only supports Record.");
                    }
                }

                Column column = field.getAnnotation(Column.class);
                // Use the column#length as the default column length, as read the system env as the override mechanism.
                // Log the error but don't block the startup sequence.
                int columnLength = column.length();

                // SQL Database extension
                SQLDatabaseExtension sqlDatabaseExtension = new SQLDatabaseExtension();
                List<SQLDatabase.CompositeIndex> indexDefinitions = new ArrayList<>();
                if (field.isAnnotationPresent(SQLDatabase.CompositeIndex.class)) {
                    indexDefinitions.add(field.getAnnotation(SQLDatabase.CompositeIndex.class));
                }

                if (field.isAnnotationPresent(SQLDatabase.CompositeIndices.class)) {
                    Collections.addAll(
                        indexDefinitions, field.getAnnotation(SQLDatabase.CompositeIndices.class).value());
                }

                indexDefinitions.forEach(indexDefinition -> {
                    sqlDatabaseExtension.appendIndex(new SQLDatabaseExtension.MultiColumnsIndex(
                            column.name(),
                            indexDefinition.withColumns()
                    ));
                });

                // ElasticSearch extension
                final ElasticSearch.MatchQuery elasticSearchAnalyzer = field.getAnnotation(
                        ElasticSearch.MatchQuery.class);
                final ElasticSearch.Column elasticSearchColumn = field.getAnnotation(ElasticSearch.Column.class);
                final ElasticSearch.Keyword keywordColumn = field.getAnnotation(ElasticSearch.Keyword.class);
                final ElasticSearch.Routing routingColumn = field.getAnnotation(ElasticSearch.Routing.class);
                ElasticSearchExtension elasticSearchExtension = new ElasticSearchExtension(
                        elasticSearchAnalyzer == null ? null : elasticSearchAnalyzer.analyzer(),
                        elasticSearchColumn == null ? null : elasticSearchColumn.legacyName(),
                        keywordColumn != null,
                        routingColumn != null
                );

                // BanyanDB extension
                final BanyanDB.SeriesID banyanDBSeriesID = field.getAnnotation(
                        BanyanDB.SeriesID.class);
                final BanyanDB.GlobalIndex banyanDBGlobalIndex = field.getAnnotation(
                        BanyanDB.GlobalIndex.class);
                final BanyanDB.NoIndexing banyanDBNoIndex = field.getAnnotation(
                        BanyanDB.NoIndexing.class);
                final BanyanDB.IndexRule banyanDBIndexRule = field.getAnnotation(
                        BanyanDB.IndexRule.class);
                final BanyanDB.MeasureField banyanDBMeasureField = field.getAnnotation(
                        BanyanDB.MeasureField.class);
                final BanyanDB.TopNAggregation topNAggregation = field.getAnnotation(
                        BanyanDB.TopNAggregation.class);
                BanyanDBExtension banyanDBExtension = new BanyanDBExtension(
                        banyanDBSeriesID == null ? -1 : banyanDBSeriesID.index(),
                        banyanDBGlobalIndex != null,
                        banyanDBNoIndex == null && !column.storageOnly(),
                        banyanDBIndexRule == null ? BanyanDB.IndexRule.IndexType.INVERTED : banyanDBIndexRule.indexType(),
                        banyanDBMeasureField != null
                );

                if (topNAggregation != null) {
                    BanyanDBModelExtension.TopN topN = new BanyanDBModelExtension.TopN();
                    topN.setLruSize(topNAggregation.lruSize());
                    topN.setCountersNumber(topNAggregation.countersNumber());
                    topN.setGroupByTagNames(Collections.singletonList(column.name()));
                    banyanDBModelExtension.setTopN(topN);
                }

                final ModelColumn modelColumn = new ModelColumn(
                        new ColumnName(column),
                        field.getType(),
                        field.getGenericType(),
                        column.storageOnly(),
                        column.indexOnly(),
                        column.dataType().isValue(),
                        columnLength,
                        sqlDatabaseExtension,
                        elasticSearchExtension,
                        banyanDBExtension
                );
                if (banyanDBExtension.isShardingKey()) {
                    checker.accept(modelName, modelColumn);
                }

                if (field.isAnnotationPresent(SQLDatabase.AdditionalEntity.class)) {
                    final var additionalEntity = field.getAnnotation(SQLDatabase.AdditionalEntity.class);
                    final var additionalTableNames = additionalEntity.additionalTables();
                    for (final var tableName : additionalTableNames) {
                        sqlDBModelExtension.appendAdditionalTable(tableName, modelColumn);
                    }
                    if (!additionalEntity.reserveOriginalColumns()) {
                        sqlDBModelExtension.appendExcludeColumns(modelColumn);
                    }
                }

                modelColumns.add(modelColumn);
                if (log.isDebugEnabled()) {
                    log.debug("The field named [{}] with the [{}] type", column.name(), field.getType());
                }
                if (column.dataType().isValue()) {
                    ValueColumnMetadata.INSTANCE.putIfAbsent(
                            modelName, column.name(),
                            column.dataType(), column.function(), column.defaultValue(), scopeId
                    );
                }
            }
        }

        if (Objects.nonNull(clazz.getSuperclass())) {
            retrieval(clazz.getSuperclass(), modelName, modelColumns, scopeId, checker, sqlDBModelExtension, banyanDBModelExtension);
        }
    }

    @Override
    public void overrideColumnName(String columnName, String newName) {
        columnNameOverrideRule.put(columnName, newName);
        models.forEach(this::followColumnNameRules);
        ValueColumnMetadata.INSTANCE.overrideColumnName(columnName, newName);
    }

    private void followColumnNameRules(Model model) {
        columnNameOverrideRule.forEach((oldName, newName) -> {
            model.getColumns().forEach(column -> {
                log.debug("Override model column name: [{}] {} -> {}.", model.getName(), oldName, newName);
                column.getColumnName().overrideName(oldName, newName);
                column.getSqlDatabaseExtension()
                        .getIndices()
                        .forEach(extraQueryIndex -> extraQueryIndex.overrideName(oldName, newName));
            });
        });
    }

    private boolean addExtraColumn4AdditionalEntity(SQLDatabaseModelExtension sqlDBModelExtension,
                                                    List<ModelColumn> modelColumns,
                                                    String extraColumn, List<String> additionalTables) {
        for (ModelColumn modelColumn : modelColumns) {
            if (modelColumn.getColumnName().getName().equals(extraColumn)) {
                additionalTables.forEach(tableName -> {
                    sqlDBModelExtension.appendAdditionalTable(tableName, modelColumn);
                });
                return true;
            }
        }
        return false;
    }

    @Override
    public List<Model> allModels() {
        return models;
    }

    private static class ShardingKeyChecker {
        private final ArrayList<ModelColumn> keys = new ArrayList<>();

        /**
         * @throws IllegalStateException if sharding key indices are conflicting.
         */
        private void accept(String modelName, ModelColumn modelColumn) throws IllegalStateException {
            final int idx = modelColumn.getBanyanDBExtension().getShardingKeyIdx();
            while (idx + 1 > keys.size()) {
                keys.add(null);
            }
            ModelColumn exist = keys.get(idx);
            if (exist != null) {
                throw new IllegalStateException(
                        modelName + "'s "
                                + "Column [" + exist.getColumnName() + "] and column [" + modelColumn.getColumnName()
                                + " are conflicting with sharding key index=" + modelColumn.getBanyanDBExtension()
                                .getShardingKeyIdx());
            }
            keys.set(idx, modelColumn);
        }

        /**
         * @param modelName model name of the entity
         * @throws IllegalStateException if sharding key indices are not continuous
         */
        private void check(String modelName) throws IllegalStateException {
            for (int i = 0; i < keys.size(); i++) {
                final ModelColumn modelColumn = keys.get(i);
                if (modelColumn == null) {
                    throw new IllegalStateException("Sharding key index=" + i + " is missing in " + modelName);
                }
            }
        }
    }
}
