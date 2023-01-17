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
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;

/**
 * BanyanDBExtension represents extra metadata for columns, but specific for BanyanDB usages.
 *
 * @since 9.1.0
 */
@RequiredArgsConstructor
public class BanyanDBExtension {
    /**
     * Sharding key is used to group time series data per metric of one entity. See {@link
     * BanyanDB.SeriesID#index()}.
     *
     * @since 9.1.0 moved into BanyanDBExtension
     * @since 9.0.0 added into {@link ModelColumn}
     */
    @Getter
    private final int shardingKeyIdx;

    /**
     * Global index column.
     *
     * @since 9.1.0
     */
    @Getter
    private final boolean isGlobalIndexing;

    /**
     * {@link BanyanDB.NoIndexing} exists to override {@link ModelColumn#shouldIndex()}, or be the same as {@link
     * ModelColumn#shouldIndex()}.
     *
     * @since 9.1.0
     */
    private final boolean shouldIndex;

    /**
     * indexType is the type of index built for a {@link ModelColumn} in BanyanDB.
     *
     * @since 9.3.0
     */
    @Getter
    private final BanyanDB.IndexRule.IndexType indexType;

    /**
     *  A column belong to a measure's field.
     */
    @Getter
    private final boolean isMeasureField;

    /**
     * @return true if this column is a part of sharding key
     */
    public boolean isShardingKey() {
        return this.shardingKeyIdx > -1;
    }

    /**
     * @return true if this column should be indexing in the BanyanDB
     */
    public boolean shouldIndex() {
        return shouldIndex;
    }
}
