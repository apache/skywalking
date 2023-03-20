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

package org.apache.skywalking.oap.server.core.storage.model;

import lombok.Getter;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;

import java.lang.reflect.Type;

@Getter
@ToString
public class ModelColumn {
    private final ColumnName columnName;
    private final Class<?> type;
    private final Type genericType;
    /**
     * Storage this column for query result, but can't be as a condition . Conflict with {@link #indexOnly}
     */
    private final boolean storageOnly;
    /**
     * Index this column for query condition only. Conflict with {@link #storageOnly}
     *
     * @since 9.0.0
     */
    private final boolean indexOnly;
    /**
     * The max length of column value for length sensitive database.
     */
    private final int length;

    /**
     * Hold configurations especially for SQL Database, such as MySQL, H2, PostgreSQL
     *
     * @since 9.1.0
     */
    private final SQLDatabaseExtension sqlDatabaseExtension;
    /**
     * Hold configurations especially for ElasticSearch
     *
     * @since 9.1.0
     */
    private final ElasticSearchExtension elasticSearchExtension;
    /**
     * Hold configurations especially for BanyanDB relevant
     *
     * @since 9.1.0
     */
    private final BanyanDBExtension banyanDBExtension;

    public ModelColumn(ColumnName columnName,
                       Class<?> type,
                       Type genericType,
                       boolean storageOnly,
                       boolean indexOnly,
                       boolean isValue,
                       int length,
                       SQLDatabaseExtension sqlDatabaseExtension,
                       ElasticSearchExtension elasticSearchExtension,
                       BanyanDBExtension banyanDBExtension) {
        this.columnName = columnName;
        this.type = type;
        this.genericType = genericType;
        this.length = length;
        this.sqlDatabaseExtension = sqlDatabaseExtension;
        this.elasticSearchExtension = elasticSearchExtension;
        /*
         * byte[] and {@link IntKeyLongValueHashMap} could never be queried.
         */
        if (type.equals(byte[].class) || type.equals(DataTable.class)) {
            this.storageOnly = true;
        } else {
            if (storageOnly && isValue) {
                throw new IllegalArgumentException(
                    "The column " + columnName + " can't be defined as both isValue and storageOnly.");
            }
            this.storageOnly = storageOnly;
        }

        if (storageOnly && indexOnly) {
            throw new IllegalArgumentException(
                "The column " + columnName + " can't be defined as both indexOnly and storageOnly.");
        }
        this.indexOnly = indexOnly;
        this.banyanDBExtension = banyanDBExtension;
    }

    /**
     * @return true means this column should be indexed, as it would be a query condition.
     */
    public boolean shouldIndex() {
        return !storageOnly;
    }
}
