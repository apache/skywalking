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
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;

/**
 * ElasticSearchExtension represents extra metadata for columns, but specific for ElasticSearch usages.
 *
 * @since 9.1.0
 */
@Getter
@RequiredArgsConstructor
public class ElasticSearchExtension {
    /**
     * The analyzer policy appointed to fuzzy query, especially for ElasticSearch.
     * When it is null, it means no need to build match query, no `copy_to` column, and no analyzer assigned.
     */
    private final ElasticSearch.MatchQuery.AnalyzerType analyzer;

    private final String legacyColumnName;

    private final boolean isKeyword;

    private final boolean isRouting;

    public boolean needMatchQuery() {
        return analyzer != null;
    }
}
