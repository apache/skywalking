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

/**
 * BanyanDBExtension represents extra metadata for columns, but specific for BanyanDB usages.
 *
 * @since 9.1.0
 */
@Getter
@RequiredArgsConstructor
public class BanyanDBExtension {
    /**
     * Sharding key is used to group time series data per metric of one entity. See {@link
     * org.apache.skywalking.oap.server.core.storage.annotation.BanyanDBShardingKey#index()}.
     *
     * @since 9.1.0 moved into BanyanDBExtension
     * @since 9.0.0 added into {@link ModelColumn}
     */
    private final int shardingKeyIdx;

    /**
     * Global index column values has 3 conditions
     * 1. NULL, this column should be as a global index.
     * 2. Empty array(declared by @BanyanDBGlobalIndex(extraFields = {}) in codes) represents this single column should
     * be a global index.
     * 3. Not empty array(declared by @BanyanDBGlobalIndex(extraFields = {"col1", "col2"}) in codes) represents this
     * column and other declared columns should be as a global index. The values of these columns should be joint by
     * underline(_)
     *
     * @since 9.1.0
     */
    private final String[] globalIndexColumns;

    /**
     * @return true if this column is a part of sharding key
     */
    public boolean isShardingKey() {
        return this.shardingKeyIdx > -1;
    }

    /**
     * @return null or array, see {@link #globalIndexColumns} for more details.
     */
    public boolean isGlobalIndexing() {
        return globalIndexColumns != null;
    }
}
