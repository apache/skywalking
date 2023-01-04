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

package org.apache.skywalking.oap.server.core.storage.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.apache.skywalking.oap.server.core.analysis.record.Record;

/**
 * BanyanDB annotation is a holder including all annotations for BanyanDB storage
 *
 * @since 9.1.0
 */
public @interface BanyanDB {
    /**
     * GlobalIndex declares advanced global index, which are only available in BanyanDB.
     * <p>
     * Global index should only be considered if a column value has a huge value candidates, but we will need a direct
     * equal
     * query without timestamp.
     * The typical global index is designed for huge candidate of indexed values, such as `trace ID` or `segment ID`.
     * <p>
     * Only work with {@link Column}
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface GlobalIndex {
    }

    /**
     * Series key is used to group time series data per metric of one entity in one place.
     *
     * For example,
     * ServiceA's traffic gauge, service call per minute, includes following timestamp values, then it should be sharded
     * by service ID
     * [ServiceA(encoded ID): 01-28 18:30 values-1, 01-28 18:31 values-2, 01-28 18:32 values-3, 01-28 18:32 values-4]
     * <p>
     * BanyanDB is the 1st storage implementation supporting this. It would make continuous time series metrics stored
     * closely and compressed better.
     * <p>
     * 1. One entity could have multiple sharding keys
     * 2. If no column is appointed for this, {@link org.apache.skywalking.oap.server.core.storage.StorageData#id}
     * would be used by the storage implementation accordingly.
     * <p>
     * NOTICE, this sharding concept is NOT just for splitting data into different database instances or physical
     * files.
     * <p>
     * Only work with {@link Column}
     *
     * @return non-negative if this column be used for sharding. -1 means not as a sharding key
     * @since 9.3.0 Rename as SeriesID.
     * @since 9.1.0 created as a new annotation.
     * @since 9.0.0 added in {@link Column}
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface SeriesID {
        /**
         * Relative entity tag
         *
         * @return index, from zero.
         */
        int index() default -1;
    }

    /**
     * Force disabling indexing declare through {@link Column}.
     * In BanyanDB, {@link GlobalIndex} provides the high performance capability to filter trace/log from super large
     * dataset, some additional conditions could be done in server memory, no indexing required in this case.
     *
     * @since 9.1.0
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface NoIndexing {

    }

    /**
     * Additional information for constructing Index in BanyanDB.
     *
     * @since 9.3.0
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface IndexRule {
        /**
         * IndexRule supports selecting two distinct kinds of index structures.
         */
        IndexType indexType() default IndexType.INVERTED;

        enum IndexType {
            /**
             * The `INVERTED` index is the primary option when users set up an index rule.
             * It's suitable for most tag indexing due to a better memory usage ratio and query performance.
             */
            INVERTED,
            /**
             * The `TREE` index could be better when there are high cardinalities, such as the `ID` tag and numeric duration tag.
             * In these cases, it saves much memory space.
             */
            TREE;
        }
    }

    /**
     * timestampColumn is to identify which column in {@link Record} is providing the timestamp(millisecond) for BanyanDB.
     * BanyanDB stream requires a timestamp in milliseconds.
     * @since 9.3.0
     */
    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface TimestampColumn {
        String value();
    }

    /**
     * MeasureField defines a column as a measure's field.
     * @since 9.4.0
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface MeasureField {
    }

    /**
     * StoreIDTag indicates a metric store its ID as a tag for searching.
     * @Since 9.4.0
     */
    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface StoreIDTag {
    }
}
