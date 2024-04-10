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

import org.apache.skywalking.oap.server.core.storage.model.ModelManipulator;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import lombok.Getter;

/**
 * Data column of all persistent entity.
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Column {
    /**
     * Column name in the storage. Most of the storage will keep the name consistently. But in same cases, this name
     * could be a keyword, then, the implementation will use {@link ModelManipulator} to replace the column name.
     * <p>
     * Be careful not to use the same column name for two models with the same type (metrics/record), which causes
     * column conflicts in storage implementations that merge all metrics/records models into a single table/index.
     * Also check {@code legacyName()}.
     */
    String name();

    /**
     * The default value of this column, when its {@link #dataType()} != {@link ValueDataType#NOT_VALUE}.
     */
    int defaultValue() default 0;

    /**
     * The column is just saved, never used as a query condition.
     */
    boolean storageOnly() default false;

    /**
     * The column(field) is just indexed, never stored(not available in query projection).
     * Note: this feature only supported by elasticsearch.
     */
    boolean indexOnly() default false;

    /**
     * @return the length of this column, this is only for {@link String} column. The usage of this depends on the
     * storage implementation.
     *
     * Notice, different lengths may cause different types. Such as, over 16383 would make the type in MySQL to be
     * MEDIUMTEXT, due to database varchar max=16383.
     * @since 7.1.0
     */
    int length() default 200;

    /**
     * Column with data type != {@link ValueDataType#NOT_VALUE} represents this is a value column. Indicate it would be
     * queried by UI/CLI.
     *
     * @return the data type of this value column. The value column is the query related value Set {@link
     * ValueDataType#NOT_VALUE} if this is not the value column, read {@link ValueDataType} for more details.
     * @since 8.0.0
     */
    ValueDataType dataType() default ValueDataType.NOT_VALUE;

    /**
     * Since 10.0.0, multi-value column is deprecated. Use {@link ValueDataType#LABELED_VALUE} instead.
     * This annotation is used to mark the column is a multi-value column for compatibility.
     * @return if this column is a multi-value column.
     */
    @Deprecated
    boolean multiIntValues() default false;

    /**
     * ValueDataType represents the data structure of value column. The persistent way of the value column determine the
     * available ways to query the data.
     */
    enum ValueDataType {
        /**
         * NOT_VALUE represents this value wouldn't be queried directly through metrics v2 protocol. It could be never
         * queried, or just through hard code to do so, uch as the lines of topology and service.
         */
        NOT_VALUE(false),
        /**
         * COMMON_VALUE represents a single value, usually int or long.
         */
        COMMON_VALUE(true),
        /**
         * LABELLED_VALUE represents this metrics have multiple values with different labels.
         */
        LABELED_VALUE(true),
        /**
         * HISTOGRAM represents the values are grouped by the buckets, usually suitable for heatmap query.
         */
        HISTOGRAM(true),
        /**
         * SAMPLED_RECORD represents the values are detail data, being persistent by following some sampled rules.
         * Usually do topn query based on value column value ASC or DESC.
         */
        SAMPLED_RECORD(true);

        @Getter
        private boolean isValue = false;

        ValueDataType(final boolean isValue) {
            this.isValue = isValue;
        }
    }
}
