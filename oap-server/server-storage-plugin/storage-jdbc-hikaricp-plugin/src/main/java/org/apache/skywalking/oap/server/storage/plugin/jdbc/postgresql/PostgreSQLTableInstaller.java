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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.postgresql;

import com.google.gson.JsonObject;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.mysql.MySQLTableInstaller;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

public class PostgreSQLTableInstaller extends MySQLTableInstaller {

    public PostgreSQLTableInstaller(Client client, ModuleManager moduleManager, int maxSizeOfArrayColumn,
                                    int numOfSearchableValuesPerTag) {
        super(client, moduleManager, maxSizeOfArrayColumn, numOfSearchableValuesPerTag);
    }

    @Override
    protected String transform(ModelColumn column, Class<?> type, Type genericType) {
        final String storageName = column.getColumnName().getStorageName();
        if (Integer.class.equals(type) || int.class.equals(type) || NodeType.class.equals(type)) {
            return storageName + " INT";
        } else if (Long.class.equals(type) || long.class.equals(type)) {
            return storageName + " BIGINT";
        } else if (Double.class.equals(type) || double.class.equals(type)) {
            return storageName + " DOUBLE PRECISION";
        } else if (String.class.equals(type)) {
            return storageName + " VARCHAR(" + column.getLength() + ")";
        } else if (StorageDataComplexObject.class.isAssignableFrom(type)) {
            return storageName + " VARCHAR(20000)";
        } else if (byte[].class.equals(type)) {
            return storageName + " TEXT";
        } else if (JsonObject.class.equals(type)) {
            return storageName + " VARCHAR(" + column.getLength() + ")";
        } else if (List.class.isAssignableFrom(type)) {
            final Type elementType = ((ParameterizedType) genericType).getActualTypeArguments()[0];
            String oneColumnType = transform(column, (Class<?>) elementType, elementType);
            // Remove the storageName as prefix
            oneColumnType = oneColumnType.substring(storageName.length());
            StringBuilder columns = new StringBuilder();
            for (int i = 0; i < maxSizeOfArrayColumn; i++) {
                columns.append(storageName).append("_").append(i).append(oneColumnType)
                       .append(i == maxSizeOfArrayColumn - 1 ? "" : ",");
            }
            return columns.toString();
        } else {
            throw new IllegalArgumentException("Unsupported data type: " + type.getName());
        }
    }

    @Override
    protected String getColumn(final ModelColumn column) {
        final String storageName = column.getColumnName().getStorageName();
        final Class<?> type = column.getType();
        if (StorageDataComplexObject.class.isAssignableFrom(type)) {
            return storageName + " TEXT";
        } else if (String.class.equals(type)) {
            if (column.getLength() > 16383) {
                return storageName + " TEXT";
            } else {
                return storageName + " VARCHAR(" + column.getLength() + ")";
            }
        }
        return super.getColumn(column);
    }
}
