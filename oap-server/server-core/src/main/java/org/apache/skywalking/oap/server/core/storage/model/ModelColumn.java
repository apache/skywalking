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
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;

@Getter
public class ModelColumn {
    private final ColumnName columnName;
    private final Class<?> type;
    private final Type genericType;
    private final boolean matchQuery;
    private final boolean storageOnly;
    private final int length;

    public ModelColumn(ColumnName columnName,
                       Class<?> type,
                       Type genericType,
                       boolean matchQuery,
                       boolean storageOnly,
                       boolean isValue,
                       int length) {
        this.columnName = columnName;
        this.type = type;
        this.genericType = genericType;
        this.matchQuery = matchQuery;
        this.length = length;
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
    }
}
