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
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import lombok.Getter;
import org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord;
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
     * ShardingKey is used to group time series data per metric in one place. Optional. Only support Measure Tag.
     * If ShardingKey is not set, the default ShardingKey is based on the combination of 'name' and 'entity' according to the {@link SeriesID}.
     * <p>
     * The typical scenario to specify the ShardingKey to the Group tag when the metric generate a TopNAggregation:
     * If not set, the default data distribution based on the combination of 'name' and 'entity', can lead to performance issues when calculating the 'TopNAggregation'.
     * This is because each shard only has a subset of the top-n list, and the query process has to be responsible for aggregating those lists to obtain the final result.
     * This introduces overhead in terms of querying performance and disk usage.
     *
     * @since 10.3.0
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface ShardingKey {
        /**
         * Relative sharding tag
         * <p>
         * The index number determines the order of the column placed in the ShardingKey.
         *
         * @return non-negative if this column be used for sharding. -1 means not as a sharding key
         */
        int index() default -1;
    }

    /**
     * Force disabling indexing declare through {@link Column}.
     * In BanyanDB, some additional conditions could be done in server memory, no indexing required in this case.
     * In the Trace model, no indexing means no tag would be created in BanyanDB.
     * In the Stream model, no indexing means no index rule would be created in BanyanDB.
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
             * The `SKIPPING` index is optimized for the majority of stream tags, which prioritizes efficient space utilization.
             * Such as the `trace_id` in the {@link LogRecord}.
             */
            SKIPPING,
            /**
             * The `TREE` index is designed for storing hierarchical data.
             * Such as Trace Span.
             */
            TREE
        }
    }

    /**
     * timestampColumn is to identify which column in {@link Record} is providing the timestamp(millisecond) for BanyanDB.
     * BanyanDB stream requires a timestamp in milliseconds.
     * Notice, the timestamp column would not create an index rule in BanyanDB.
     *
     * @since 9.3.0
     */
    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface TimestampColumn {
        String value();
    }

    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Trace {
        @Target({ElementType.TYPE})
        @Retention(RetentionPolicy.RUNTIME)
        @interface TraceIdColumn {
            String value();
        }

        @Target({ElementType.TYPE})
        @Retention(RetentionPolicy.RUNTIME)
        @interface SpanIdColumn {
            String value();
        }

        /**
         * IndexRule is used to define a composite index in BanyanDB.
         * Notice, the order of columns is significant, the columns in front have a higher priority
         * and more efficient in searching.
         */
        @Target({ElementType.TYPE})
        @Retention(RetentionPolicy.RUNTIME)
        @Repeatable(IndexRule.List.class)
        @interface IndexRule {
            String name();
            String[] columns();
            String orderByColumn();
            @Target({ElementType.TYPE})
            @Retention(RetentionPolicy.RUNTIME)
            @interface List {
                IndexRule[] value();
            }
        }
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
     * If a metric is annotated with IndexMode, the installer will automatically create a virtual String tag 'id' for the SeriesID.
     * We don't need to declare other columns as SeriesID.
     * The value of 'id' is generated by the {@link Metrics#id()} method.
     * <p>
     * The entity should be a kind of metadata entity, e.g. ServiceTraffic.
     * The return({@link StorageID} of {@link Metrics#id()} should not include any time relative column and column name.
     * <pre>
     * <code>
     *         return new StorageID().append(id);
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

    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Group {
        /**
         * Specify the group name for the Stream (Record). The default value is "NONE".
         */
        StreamGroup streamGroup() default StreamGroup.NONE;

        TraceGroup traceGroup() default TraceGroup.NONE;
    }

    enum StreamGroup {
        RECORDS("records"),
        RECORDS_LOG("recordsLog"),
        RECORDS_BROWSER_ERROR_LOG("recordsBrowserErrorLog"),
        NONE("none");
        @Getter
        private final String name;

        StreamGroup(final String name) {
            this.name = name;
        }
    }

    enum MeasureGroup {
        METRICS_MINUTE("metricsMinute"),
        METRICS_HOUR("metricsHour"),
        METRICS_DAY("metricsDay"),
        METADATA("metadata");
        @Getter
        private final String name;

        MeasureGroup(final String name) {
            this.name = name;
        }
    }

    enum PropertyGroup {
        PROPERTY("property");

        @Getter
        private final String name;

        PropertyGroup(final String name) {
            this.name = name;
        }
    }

    enum TraceGroup {
        TRACE("trace"),
        ZIPKIN_TRACE("zipkinTrace"),
        NONE("none");

        @Getter
        private final String name;

        TraceGroup(final String name) {
            this.name = name;
        }
    }
}
