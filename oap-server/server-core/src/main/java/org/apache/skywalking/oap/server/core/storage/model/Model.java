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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.record.Record;

import java.util.List;

/**
 * The model definition of a logic entity.
 */
@Getter
@EqualsAndHashCode
@RequiredArgsConstructor
public class Model {
    private final String name;
    private final List<ModelColumn> columns;
    private final int scopeId;
    private final DownSampling downsampling;
    private final boolean superDataset;
    private final Class<?> streamClass;
    private final boolean timeRelativeID;
    /**
     * BanyanDB only — when true, the BanyanDB installer is allowed to apply purely
     * additive shape changes (new tag / new field) at boot. See
     * {@link org.apache.skywalking.oap.server.core.analysis.Stream#allowBootReshape()}.
     * JDBC and Elasticsearch ignore this flag (append-only data paths already accept
     * additive column / mapping additions without operator intervention). Defaults to
     * false for models registered without a {@code @Stream} annotation (e.g. runtime-rule
     * MAL / LAL metrics, which reshape through the runtime-rule REST path instead).
     */
    private final boolean allowBootReshape;
    private final SQLDatabaseModelExtension sqlDBModelExtension;
    private final BanyanDBModelExtension banyanDBModelExtension;
    private final ElasticSearchModelExtension elasticSearchModelExtension;

    @Getter(lazy = true)
    private final boolean isMetric = Metrics.class.isAssignableFrom(getStreamClass());
    @Getter(lazy = true)
    private final boolean isRecord = Record.class.isAssignableFrom(getStreamClass());
    @Getter(lazy = true)
    private final boolean isTimeSeries = !DownSampling.None.equals(getDownsampling());
}
