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

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.storage.ShardingAlgorithm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @since 9.1.0
 */
@Getter
@EqualsAndHashCode
public class SQLDatabaseModelExtension {
    private final Map<String, AdditionalTable> additionalTables = new HashMap<>(5);
    //exclude the columns from the main table
    private final List<ModelColumn> excludeColumns = new ArrayList<>(5);

    @Setter
    private Optional<Sharding> sharding = Optional.empty();

    public void appendAdditionalTable(String tableName, ModelColumn column) {
        additionalTables.computeIfAbsent(tableName, AdditionalTable::new)
                        .appendColumn(column);
    }

    public void appendExcludeColumns(ModelColumn column) {
        excludeColumns.add(column);
    }

    public boolean isShardingTable() {
        return this.sharding.isPresent() && !this.sharding.get().getShardingAlgorithm().equals(ShardingAlgorithm.NO_SHARDING);
    }

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class AdditionalTable {
        private final String name;
        private final List<ModelColumn> columns = new ArrayList<>();
        private boolean hasListColumn = false;

        public void appendColumn(ModelColumn column) {
            if (hasListColumn && List.class.isAssignableFrom(column.getType())) {
                throw new IllegalStateException("A AdditionalEntity: " + name + " only support 1 List type. Field: " + column.getColumnName() +
                    " should set to another AdditionalEntity.");
            } else if (List.class.isAssignableFrom(column.getType())) {
                hasListColumn = true;
            }
            columns.add(column);
        }
    }

    @Getter
    @RequiredArgsConstructor
    public static class Sharding {
        private final ShardingAlgorithm shardingAlgorithm;
        private final String dataSourceShardingColumn;
        private final String tableShardingColumn;
    }
}
