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
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

public class ElasticSearchModelExtension {

    /**
     * Routing defines a field of {@link Record} to control the sharding policy.
     */
    @Getter
    private String routing;

    public void setRouting(String modelName, List<ModelColumn> modelColumns) throws IllegalStateException {
        if (CollectionUtils.isEmpty(modelColumns)) {
            return;
        }

        List<ModelColumn> routingColumns = modelColumns.stream()
                .filter(col -> col.getElasticSearchExtension().isRouting())
                .collect(Collectors.toList());

        int size = routingColumns.size();
        if (size > 1) {
            throw new IllegalStateException(modelName + "'s routing field is duplicated "
                    + routingColumns.stream()
                            .map(col -> col.getColumnName().toString())
                            .collect(Collectors.joining(",", "[", "]")));
        }

        if (size == 1) {
            routing = routingColumns.get(0).getColumnName().getName();
        }
    }
}
