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

package org.apache.skywalking.oap.server.core.storage.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import org.apache.skywalking.oap.server.core.analysis.indicator.annotation.IndicatorAnnotationUtils;
import org.apache.skywalking.oap.server.core.annotation.AnnotationListener;
import org.apache.skywalking.oap.server.core.source.Scope;
import org.apache.skywalking.oap.server.core.storage.model.ColumnName;
import org.apache.skywalking.oap.server.core.storage.model.IModelGetter;
import org.apache.skywalking.oap.server.core.storage.model.IModelOverride;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class StorageAnnotationListener implements AnnotationListener, IModelGetter, IModelOverride {

    private static final Logger logger = LoggerFactory.getLogger(StorageAnnotationListener.class);

    @Getter private final List<Model> models;

    public StorageAnnotationListener() {
        this.models = new LinkedList<>();
    }

    @Override public Class<? extends Annotation> annotation() {
        return StorageEntity.class;
    }

    @Override public void notify(Class aClass) {
        logger.info("The owner class of storage annotation, class name: {}", aClass.getName());

        String modelName = StorageEntityAnnotationUtils.getModelName(aClass);
        boolean deleteHistory = StorageEntityAnnotationUtils.getDeleteHistory(aClass);
        Scope sourceScope = StorageEntityAnnotationUtils.getSourceScope(aClass);
        List<ModelColumn> modelColumns = new LinkedList<>();
        boolean isIndicator = IndicatorAnnotationUtils.isIndicator(aClass);
        retrieval(aClass, modelName, modelColumns);

        models.add(new Model(modelName, modelColumns, isIndicator, deleteHistory, sourceScope));
    }

    private void retrieval(Class clazz, String modelName, List<ModelColumn> modelColumns) {
        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            if (field.isAnnotationPresent(Column.class)) {
                Column column = field.getAnnotation(Column.class);
                modelColumns.add(new ModelColumn(new ColumnName(column.columnName()), field.getType(), column.matchQuery()));
                if (logger.isDebugEnabled()) {
                    logger.debug("The field named {} with the {} type", column.columnName(), field.getType());
                }
                if (column.isValue()) {
                    ValueColumnIds.INSTANCE.putIfAbsent(modelName, column.columnName(), column.function());
                }
            }
        }

        if (Objects.nonNull(clazz.getSuperclass())) {
            retrieval(clazz.getSuperclass(), modelName, modelColumns);
        }
    }

    @Override public void overrideColumnName(String columnName, String newName) {
        models.forEach(model -> {
            model.getColumns().forEach(column -> {
                ColumnName existColumnName = column.getColumnName();
                String name = existColumnName.getName();
                if (name.equals(columnName)) {
                    existColumnName.setStorageName(newName);
                    logger.debug("Model {} column {} has been override. The new column name is {}.", model.getName(), name, newName);
                }
            });
        });
    }
}
