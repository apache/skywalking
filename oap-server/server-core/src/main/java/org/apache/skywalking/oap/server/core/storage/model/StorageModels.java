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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    public Model add(Class<?> aClass, int scopeId, Storage storage, boolean record) throws StorageException {
        // Check this scope id is valid.
        DefaultScopeDefine.nameOf(scopeId);

        List<ModelColumn> modelColumns = new ArrayList<>();
        ShardingKeyChecker checker = new ShardingKeyChecker();
        SQLDatabaseModelExtension sqlDBModelExtension = new SQLDatabaseModelExtension();
        BanyanDBModelExtension banyanDBModelExtension = new BanyanDBModelExtension();
        retrieval(aClass, storage.getModelName(), modelColumns, scopeId, checker, sqlDBModelExtension, record);
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
        //Add Records timestampColumn for BanyanDB
        if (Record.class.isAssignableFrom(aClass)) {
            if (aClass.isAnnotationPresent(BanyanDB.TimestampColumn.class)) {
                String timestampColumn = aClass.getAnnotation(BanyanDB.TimestampColumn.class).value();
                banyanDBModelExtension.setTimestampColumn(timestampColumn);
            } else {
                throw new IllegalStateException(
                    "Record model [" + storage.getModelName() + "] miss defined @BanyanDB.TimestampColumn");
            }
        }

        checker.check(storage.getModelName());

        Model model = new Model(
            storage.getModelName(),
            modelColumns,
            scopeId,
            storage.getDownsampling(),
            record,
            isSuperDatasetModel(aClass),
            aClass,
            storage.isTimeRelativeID(),
            sqlDBModelExtension,
            banyanDBModelExtension
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
     * CreatingListener listener could react when {@link #add(Class, int, Storage, boolean)} model happens. Also, the
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
                           boolean record) {
        if (log.isDebugEnabled()) {
            log.debug("Analysis {} to generate Model.", clazz.getName());
        }

        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            if (field.isAnnotationPresent(Column.class)) {
                if (field.isAnnotationPresent(SQLDatabase.AdditionalEntity.class)) {
                    if (!record) {
                        throw new IllegalStateException("Model [" + modelName + "] is not a Record, @SQLDatabase.AdditionalEntity only supports Record.");
                    }
                }

                Column column = field.getAnnotation(Column.class);
                // Use the column#length as the default column length, as read the system env as the override mechanism.
                // Log the error but don't block the startup sequence.
                int columnLength = column.length();

                // SQL Database extension
                SQLDatabaseExtension sqlDatabaseExtension = new SQLDatabaseExtension();
                List<SQLDatabase.QueryUnifiedIndex> indexDefinitions = new ArrayList<>();
                if (field.isAnnotationPresent(SQLDatabase.QueryUnifiedIndex.class)) {
                    indexDefinitions.add(field.getAnnotation(SQLDatabase.QueryUnifiedIndex.class));
                }

                if (field.isAnnotationPresent(SQLDatabase.MultipleQueryUnifiedIndex.class)) {
                    Collections.addAll(
                        indexDefinitions, field.getAnnotation(SQLDatabase.MultipleQueryUnifiedIndex.class).value());
                }

                indexDefinitions.forEach(indexDefinition -> {
                    sqlDatabaseExtension.appendIndex(new SQLDatabaseExtension.MultiColumnsIndex(
                        column.columnName(),
                        indexDefinition.withColumns()
                    ));
                });

                // ElasticSearch extension
                final ElasticSearch.MatchQuery elasticSearchAnalyzer = field.getAnnotation(
                    ElasticSearch.MatchQuery.class);
                final ElasticSearch.Column elasticSearchColumn = field.getAnnotation(ElasticSearch.Column.class);
                ElasticSearchExtension elasticSearchExtension = new ElasticSearchExtension(
                    elasticSearchAnalyzer == null ? null : elasticSearchAnalyzer.analyzer(),
                    elasticSearchColumn == null ? null : elasticSearchColumn.columnAlias()
                );

                // BanyanDB extension
                final BanyanDB.ShardingKey banyanDBShardingKey = field.getAnnotation(
                    BanyanDB.ShardingKey.class);
                final BanyanDB.GlobalIndex banyanDBGlobalIndex = field.getAnnotation(
                    BanyanDB.GlobalIndex.class);
                final BanyanDB.NoIndexing banyanDBNoIndex = field.getAnnotation(
                    BanyanDB.NoIndexing.class);
                final BanyanDB.IndexRule banyanDBIndexRule = field.getAnnotation(
                        BanyanDB.IndexRule.class);
                BanyanDBExtension banyanDBExtension = new BanyanDBExtension(
                    banyanDBShardingKey == null ? -1 : banyanDBShardingKey.index(),
                    banyanDBGlobalIndex != null,
                    banyanDBNoIndex == null && column.storageOnly(),
                    banyanDBIndexRule == null ? BanyanDB.IndexRule.IndexType.INVERTED : banyanDBIndexRule.indexType()
                );

                final ModelColumn modelColumn = new ModelColumn(
                    new ColumnName(
                        modelName,
                        column.columnName()
                    ),
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
                    String[] tableNames = field.getAnnotation(SQLDatabase.AdditionalEntity.class).additionalTables();
                    for (final String tableName : tableNames) {
                        sqlDBModelExtension.appendAdditionalTable(tableName, modelColumn);
                    }
                    if (!field.getAnnotation(SQLDatabase.AdditionalEntity.class).reserveOriginalColumns()) {
                        sqlDBModelExtension.appendExcludeColumns(modelColumn);
                    }
                }

                modelColumns.add(modelColumn);
                if (log.isDebugEnabled()) {
                    log.debug("The field named [{}] with the [{}] type", column.columnName(), field.getType());
                }
                if (column.dataType().isValue()) {
                    ValueColumnMetadata.INSTANCE.putIfAbsent(
                        modelName, column.columnName(), column.dataType(), column.function(),
                        column.defaultValue(), scopeId
                    );
                }
            }
        }

       // For the annotation need to be declared on the superclass, the other annotation should be declared on the subclass.
        if (!sqlDBModelExtension.getSharding().isPresent() && clazz.isAnnotationPresent(SQLDatabase.Sharding.class)) {
            SQLDatabase.Sharding sharding = clazz.getAnnotation(SQLDatabase.Sharding.class);
            sqlDBModelExtension.setSharding(
                Optional.of(new SQLDatabaseModelExtension.Sharding(sharding.shardingAlgorithm(),
                                                                   sharding.dataSourceShardingColumn(),
                                                                   sharding.tableShardingColumn()
                )));
        }

        if (Objects.nonNull(clazz.getSuperclass())) {
            retrieval(clazz.getSuperclass(), modelName, modelColumns, scopeId, checker, sqlDBModelExtension, record);
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

    private class ShardingKeyChecker {
        private ArrayList<ModelColumn> keys = new ArrayList<>();

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
