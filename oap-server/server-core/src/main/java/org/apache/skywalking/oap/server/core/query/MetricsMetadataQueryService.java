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

package org.apache.skywalking.oap.server.core.query;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.query.enumeration.MetricsType;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * MetricsMetadataQueryService provides the metadata of metrics to other modules.
 */
public class MetricsMetadataQueryService implements Service {
    public MetricsType typeOfMetrics(String metricsName) {
        final Optional<ValueColumnMetadata.ValueColumn> valueColumn
            = ValueColumnMetadata.INSTANCE.readValueColumnDefinition(metricsName);
        if (valueColumn.isPresent()) {
            switch (valueColumn.get().getDataType()) {
                case COMMON_VALUE:
                    return MetricsType.REGULAR_VALUE;
                case LABELED_VALUE:
                    return MetricsType.LABELED_VALUE;
                case HISTOGRAM:
                    return MetricsType.HEATMAP;
                case SAMPLED_RECORD:
                    return MetricsType.SAMPLED_RECORD;
                case NOT_VALUE:
                default:
                    return MetricsType.UNKNOWN;
            }
        } else {
            return MetricsType.UNKNOWN;
        }
    }

    public List<MetricDefinition> listMetrics(String regex) {
        return ValueColumnMetadata.INSTANCE.getAllMetadata()
                                           .entrySet()
                                           .stream()
                                           .filter(
                                               metadata ->
                                                   StringUtil.isNotEmpty(regex) ?
                                                       metadata.getKey().matches(regex) : true)
                                           .map(metadata -> new MetricDefinition(
                                                    metadata.getKey(),
                                                    typeOfMetrics(metadata.getKey()),
                                                    DefaultScopeDefine.catalogOf(metadata.getValue().getScopeId())
                                                )
                                           )
                                           .collect(Collectors.toList());
    }
}
