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
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.record.Record;

import java.util.List;

/**
 * BanyanDBExtension represents extra metadata for models, but specific for BanyanDB usages.
 *
 * @since 9.3.0
 */
public class BanyanDBModelExtension {
    /**
     * timestampColumn is to identify which column in {@link Record} is providing the timestamp(millisecond) for BanyanDB.
     * BanyanDB stream requires a timestamp in milliseconds
     *
     * @since 9.3.0
     */
    @Getter
    @Setter
    private String timestampColumn;

    /**
     * storeIDTag indicates whether a metric stores its ID as a tag.
     * The installer will create a virtual string ID tag with a tree index rule.
     */
    @Getter
    @Setter
    private boolean storeIDTag;

    @Getter
    @Setter
    private TopN topN;

    public static class TopN {
        /**
         * lru_size defines how many time_buckets are held in the memory.
         * For example, "2" means data points belonging to the latest "2" time_buckets will be persisted.
         * The default value is 2 in the BanyanDB if not set.
         *
         * @since 9.4.0
         */
        @Getter
        @Setter
        private int lruSize;

        /**
         * counters_number defines the max number of entries to be tracked during the pre-aggregation.
         * The default value is 1000 in the BanyanDB if not set.
         *
         * @since 9.4.0
         */
        @Getter
        @Setter
        private int countersNumber;

        @Setter
        @Getter
        private List<String> groupByTagNames;
    }
}
