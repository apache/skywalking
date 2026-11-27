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
import org.apache.skywalking.oap.server.core.storage.model.BanyanDBTrace;

/**
 * BanyanDB annotation is a holder including all annotations for BanyanDB storage, grouped by the BanyanDB engine a
 * model is stored in: {@link Measure} (metrics), {@link IndexMode} (metrics in index mode), {@link Stream} (records)
 * and {@link Trace} (trace records).
 *
 * @since 9.1.0
 */
public @interface BanyanDB {

    /**
     * Annotations of metrics stored as BanyanDB measures, except the ones in {@link IndexMode}.
     */
    @interface Measure {
        /**
         * Series key is used to group time series data per metric of one entity in one place.
         * <p>
         * For example, ServiceA's traffic gauge, service call per minute, includes following timestamp values, then it
         * should be sharded by service ID: [ServiceA(encoded ID): 01-28 18:30 values-1, 01-28 18:31 values-2, ...]
         * <p>
         * 1. One entity could have multiple series keys. 2. If no column is appointed for this,
         * {@link org.apache.skywalking.oap.server.core.storage.StorageData#id} is used.
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
             * The index number determines the order of the column placed in the SeriesID. BanyanDB searches series
             * by prefix, so the column most often used as a condition goes first.
             *
             * @return non-negative if this column is a part of the SeriesID. -1 means not.
             */
            int index() default -1;
        }

        /**
         * ShardingKey is used to group time series data per metric in one place. Optional, a measure tag only. Without
         * it, data is distributed by the combination of 'name' and 'entity' (see {@link SeriesID}).
         * <p>
         * The typical use is the group tag of a metric that generates a TopNAggregation: distributed by entity, each
         * shard holds only part of the top-n list, and the query has to merge the lists.
         *
         * @since 10.3.0
         */
        @Target({ElementType.FIELD})
        @Retention(RetentionPolicy.RUNTIME)
        @interface ShardingKey {
            /**
             * @return non-negative if this column is a part of the sharding key, in this order. -1 means not.
             */
            int index() default -1;
        }

        /**
         * The column is a measure field. Fields are stored apart from the tags.
         * <p>
         * Annotated: the column is a measure field. Unannotated: the column is a measure tag, indexed unless
         * {@link Column#storageOnly()} is true.
         *
         * @since 9.4.0
         */
        @Target({ElementType.FIELD})
        @Retention(RetentionPolicy.RUNTIME)
        @interface Field {
        }

        /**
         * The index rule of this tag supports sorting.
         *
         * @since 10.2.0
         */
        @Target({ElementType.FIELD})
        @Retention(RetentionPolicy.RUNTIME)
        @interface EnableSort {
        }

        /**
         * Match query with the given analyzer, a fuzzy query powered by the analyzer.
         *
         * @since 10.1.0
         */
        @Target({ElementType.FIELD})
        @Retention(RetentionPolicy.RUNTIME)
        @interface MatchQuery {
            AnalyzerType analyzer();
        }
    }

    /**
     * IndexMode marks a metric stored as a BanyanDB measure in index mode, and holds the annotations of such
     * metrics.
     * <p>
     * An IndexMode metric is a half-time series metric: it is time relative and still affected by the metric TTL, but
     * its ID doesn't include the time bucket. The entity has a unique name to represent the entity, e.g.
     * ServiceTraffic. The installer creates a virtual String tag 'id' for the SeriesID, valued by {@link Metrics#id()},
     * whose {@link StorageID} must not include any time relative column:
     * <pre>
     *     return new StorageID().append(id);
     * </pre>
     * All columns are stored in the index exclusively. When a column is not used as a query condition, only
     * {@link Column#storageOnly()} is allowed. No measure field is allowed.
     *
     * @since 10.2.0
     */
    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface IndexMode {
        /**
         * The index rule of this tag supports sorting.
         */
        @Target({ElementType.FIELD})
        @Retention(RetentionPolicy.RUNTIME)
        @interface EnableSort {
        }

        /**
         * Match query with the given analyzer, a fuzzy query powered by the analyzer.
         */
        @Target({ElementType.FIELD})
        @Retention(RetentionPolicy.RUNTIME)
        @interface MatchQuery {
            AnalyzerType analyzer();
        }
    }

    /**
     * Annotations of records stored as BanyanDB streams.
     */
    @interface Stream {
        /**
         * The group of the stream.
         */
        @Target({ElementType.TYPE})
        @Retention(RetentionPolicy.RUNTIME)
        @interface Group {
            StreamGroup value();
        }

        /**
         * The column of the {@link Record} that provides the timestamp (millisecond) BanyanDB streams require. The
         * timestamp column gets no index rule.
         *
         * @since 9.3.0
         */
        @Target({ElementType.TYPE})
        @Retention(RetentionPolicy.RUNTIME)
        @interface TimestampColumn {
            String value();
        }

        /**
         * Series key, see {@link Measure.SeriesID}.
         */
        @Target({ElementType.FIELD})
        @Retention(RetentionPolicy.RUNTIME)
        @interface SeriesID {
            int index() default -1;
        }

        /**
         * The index structure of this tag's index rule.
         *
         * @since 9.3.0
         */
        @Target({ElementType.FIELD})
        @Retention(RetentionPolicy.RUNTIME)
        @interface IndexRule {
            IndexType indexType() default IndexType.INVERTED;

            enum IndexType {
                /**
                 * The primary option, suitable for most tags thanks to its memory usage and query performance.
                 */
                INVERTED,
                /**
                 * Optimized for space, such as the `trace_id` in the {@link LogRecord}.
                 */
                SKIPPING,
                /**
                 * Designed for hierarchical data. Every {@link BanyanDB.Trace.IndexRule} is implicitly a tree index;
                 * on a stream tag, BanyanDB creates the rule but never populates it.
                 */
                TREE
            }
        }

        /**
         * The index rule of this tag supports sorting.
         *
         * @since 10.2.0
         */
        @Target({ElementType.FIELD})
        @Retention(RetentionPolicy.RUNTIME)
        @interface EnableSort {
        }

        /**
         * Match query with the given analyzer, a fuzzy query powered by the analyzer.
         *
         * @since 10.1.0
         */
        @Target({ElementType.FIELD})
        @Retention(RetentionPolicy.RUNTIME)
        @interface MatchQuery {
            AnalyzerType analyzer();
        }

        /**
         * No index rule for this tag, although {@link Column} would create one. BanyanDB filters on it in server
         * memory instead.
         *
         * @since 9.1.0
         */
        @Target({ElementType.FIELD})
        @Retention(RetentionPolicy.RUNTIME)
        @interface NoIndexing {
        }
    }

    /**
     * Annotations of records stored as BanyanDB traces.
     */
    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Trace {
        /**
         * The group of the trace. A model with {@link StoreAsAttachmentOf} takes the group of its target instead.
         */
        @Target({ElementType.TYPE})
        @Retention(RetentionPolicy.RUNTIME)
        @interface Group {
            TraceGroup value();
        }

        /**
         * The column that provides the timestamp (millisecond) of the trace.
         */
        @Target({ElementType.TYPE})
        @Retention(RetentionPolicy.RUNTIME)
        @interface TimestampColumn {
            String value();
        }

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
         * IndexRule is used to define a composite index in BanyanDB. Its index type is implicit: the installer
         * always builds it as a tree index ({@link Stream.IndexRule.IndexType#TREE}), with {@link #orderByColumn()}
         * as its last tag.
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

        /**
         * No trace tag for this column: BanyanDB holds its value only inside the span payload, which queries decode
         * to rebuild the span. Use it only for a value the record's {@link BanyanDBTrace#getSpanWrapper()} encodes.
         */
        @Target({ElementType.FIELD})
        @Retention(RetentionPolicy.RUNTIME)
        @interface InSpanPayloadOnly {
        }

        /**
         * The rows of this model are stored as an attachment of another model's trace: they are written into that
         * trace, and its query returns them with its own spans. The target supplies the trace name, its group, and
         * its trace ID, timestamp and span ID columns; this model's {@link TraceIdColumn}, {@link TimestampColumn}
         * and {@link SpanIdColumn} name the columns whose values go into them.
         */
        @Target({ElementType.TYPE})
        @Retention(RetentionPolicy.RUNTIME)
        @interface StoreAsAttachmentOf {
            /**
             * @return the model class of the target trace.
             */
            Class<?> value();
        }
    }

    /**
     * The analyzer of a match query.
     */
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

    enum StreamGroup {
        RECORDS("records"),
        RECORDS_LOG("recordsLog"),
        RECORDS_BROWSER_ERROR_LOG("recordsBrowserErrorLog"),
        /**
         * AI agent conversation files: Session Data and Session Flow rows of up to 2 MiB each, kept for weeks,
         * so they carry their own retention and shard settings apart from the log group.
         */
        RECORDS_AI_AGENT("recordsAIAgent"),
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
        OTLP_TRACE("otlpTrace"),
        NONE("none");

        @Getter
        private final String name;

        TraceGroup(final String name) {
            this.name = name;
        }
    }
}
