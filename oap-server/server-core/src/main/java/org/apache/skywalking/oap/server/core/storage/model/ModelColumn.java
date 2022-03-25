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

import java.lang.reflect.Type;
import lombok.Getter;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

@Getter
@ToString
public class ModelColumn {
    private final ColumnName columnName;
    private final Class<?> type;
    private final Type genericType;
    private final boolean matchQuery;
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
     * The analyzer policy appointed to fuzzy query, especially for ElasticSearch
     */
    private final Column.AnalyzerType analyzer;
    /**
     * Sharding key is used to group time series data per metric of one entity. See {@link Column#shardingKeyIdx()}.
     *
     * @since 9.0.0
     */
    private int shardingKeyIdx;

    public ModelColumn(ColumnName columnName,
                       Class<?> type,
                       Type genericType,
                       boolean matchQuery,
                       boolean storageOnly,
                       boolean indexOnly,
                       boolean isValue,
                       int length,
                       Column.AnalyzerType analyzer,
                       int shardingKeyIdx) {
        this.columnName = columnName;
        this.type = type;
        this.genericType = genericType;
        this.matchQuery = matchQuery;
        this.length = length;
        this.analyzer = analyzer;
        /*
         * byte[] and {@link IntKeyLongValueHashMap} could never be query.
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
        this.shardingKeyIdx = shardingKeyIdx;
    }
}
