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
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.StorageID;

/**
 * BanyanDB annotation is a holder including all annotations for BanyanDB storage
 *
 * @since 9.1.0
 */
public @interface BanyanDB {
    /**
     * Series key is used to group time series data per metric of one entity in one place.
     * <p>
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
     * @since 9.3.0 Rename as SeriesID.
     * @since 9.1.0 created as a new annotation.
     * @since 9.0.0 added in {@link Column}
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface SeriesID {
        /**
         * Relative entity tag
         * <p>
         * The index number determines the order of the column placed in the SeriesID.
         * BanyanDB SeriesID searching procedure uses a prefix-scanning strategy.
         * Searching series against a prefix could improve the performance.
         * <p>
         * For example, the ServiceTraffic composite "layer" and "name" as the SeriesID,
         * considering OAP finds services by "layer", the "layer" 's index should be 0 to
         * trigger a prefix-scanning.
         *
         * @return non-negative if this column be used for sharding. -1 means not as a sharding key
         */
        int index() default -1;
    }

    /**
     * Force disabling indexing declare through {@link Column}.
     * In BanyanDB, some additional conditions could be done in server memory, no indexing required in this case.
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
        }
    }

    /**
     * timestampColumn is to identify which column in {@link Record} is providing the timestamp(millisecond) for
     * BanyanDB.
     * BanyanDB stream requires a timestamp in milliseconds.
     *
     * @since 9.3.0
     */
    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface TimestampColumn {
        String value();
    }

    /**
     * MeasureField defines a column as a measure's field.
     * The measure field has a significant difference from no-indexing tag.
     * The measure fields are stored in another file, but no-indexing tag is stored in the same file with the indexing
     * tags.
     * <p>
     * Annotated: the column is a measure field.
     * Unannotated: the column is a measure tag.
     * storageOnly=true: the column is a measure tag that is not indexed.
     * storageOnly=false: the column is a measure tag that is indexed.
     * indexOnly=true: the column is a measure tag that is indexed, but not stored.
     * indexOnly=false: the column is a measure tag that is indexed and stored.
     *
     * @since 9.4.0
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface MeasureField {
    }

    /**
     * StoreIDTag indicates a metric store its ID as a tag for searching.
     *
     * @since 9.4.0
     */
    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface StoreIDAsTag {
    }

    /**
     * Generate a TopN Aggregation and use the annotated column as a groupBy tag.
     * It also contains parameters for TopNAggregation
     *
     * @since 9.4.0
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @interface TopNAggregation {
        /**
         * The size of LRU determines the maximally tolerated time range.
         * The buffers in the time range are kept in the memory so that
         * the data in [T - lruSize * n, T] would be accepted in the pre-aggregation process.
         * T = the current time in the current dimensionality.
         * n = interval in the current dimensionality.
         */
        int lruSize() default 2;

        /**
         * The max size of entries in a time window for the pre-aggregation.
         */
        int countersNumber() default 1000;
    }

    /**
     * Match query is designed for BanyanDB match query with specific analyzer. It is a fuzzy query implementation
     * powered by analyzer.
     *
     * @since 10.1.0
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface MatchQuery {
        AnalyzerType analyzer();

        enum AnalyzerType {
            /**
             * Keyword analyzer is a “noop” analyzer which returns the entire input string as a single token.
             */
            KEYWORD,
            /**
             * Standard analyzer provides grammar based tokenization
             */
            STANDARD,
            /**
             * Simple analyzer breaks text into tokens at any non-letter character,
             * such as numbers, spaces, hyphens and apostrophes, discards non-letter characters,
             * and changes uppercase to lowercase.
             */
            SIMPLE,
            /**
             * URL analyzer breaks test into tokens at any non-letter and non-digit character
             */
            URL
        }
    }

    /**
     * EnableSort is used to indicate the IndexRule supports sorting.
     *
     * @since 10.2.0
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface EnableSort {
    }

    /**
     * IndexMode is used to indicate the index mode of the metric.
     * IndexMode metric is a half-time series metric, which means the metric is time relative, and still affected by
     * metric TTL, but its ID doesn't include time bucket. The entity has a unique name to represent the entity.
     * <p>
     * The entity should be a kind of metadata entity, e.g. ServiceTraffic.
     * The return({@link StorageID} of {@link Metrics#id()} should not include any time relative column.
     * <pre>
     * <code>
     *         return new StorageID().appendMutant(new String[] {
     *             NAME,
     *             LAYER
     *         }, id);
     * </code>
     * </pre>
     * <p>
     * A metric with complete(not IndexMode) time series data includes the TIME_BUCKET column in the ID.
     * <pre>
     * <code>
     *          return new StorageID()
     *             .append(TIME_BUCKET, getTimeBucket())
     *             .append(ENTITY_ID, getEntityId());
     * </code>
     * </pre>
     *
     * <p>
     * All columns in the metric will be stored in the index exclusively.
     * When an entity column is not used in query condition, only {@link Column#storageOnly()} is allowed.
     * No {@link MeasureField} is allowed for those columns in IndexMode entity.
     *
     * @since 10.2.0
     */
    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface IndexMode {
    }
}
