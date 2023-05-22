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

package org.apache.skywalking.oap.server.core.source;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Define the default columns of source scope. These columns pass down into the persistent entity(OAL metrics entity)
 * automatically.
 */
@Getter
@RequiredArgsConstructor
public class ScopeDefaultColumn {
    private final String fieldName;
    private final String columnName;
    private final Class<?> type;
    private final boolean isID;
    private final int length;
    private final boolean isCompositeID;
    private final int idxOfCompositeID;
    private final boolean groupByCondInTopN;

    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface DefinedByField {
        String columnName();

        /**
         * Dynamic active means this column is only activated through core setting explicitly.
         *
         * @return FALSE: this column is not going to be added to the final generated metric as a column.
         * TRUE: this column could be added as a column if core/activeExtraModelColumns == true.
         */
        boolean requireDynamicActive() default false;

        /**
         * Define column length, only effective when the type is String.
         */
        int length() default 256;

        /**
         * Indicate whether this column is a condition for groupBy in the TopN Aggregation.
         *
         * @since 9.5.0
         */
        boolean groupByCondInTopN() default false;

        /**
         * Each metric should include all fields composing the raw ID.
         * In the previous implementation, we build an entity_id by {@link VirtualColumnDefinition#isID()} == true,
         * but in now, some storage like BanyanDB, it supports a composite ID by multiple existing fields natively,
         * so, this new composite ID is created, logically as same as the virtual ID.
         *
         * @return the index of this field when it is a part of ID. Or return -1 if not a part of ID.
         * @since 9.5.0
         */
        int idxOfCompositeID() default -1;
    }

    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface VirtualColumnDefinition {
        String fieldName();

        String columnName();

        Class type();

        /**
         * Declare this virtual column is representing an entity ID of this source and generated metrics.
         * Typically, metric ID = timestamp + entity ID
         * <p>
         * This takes {@link ISource#getEntityId()}'s return as the value.
         *
         * @return TRUE if this is an ID column.
         */
        boolean isID() default false;

        /**
         * The virtual column is used to declare the entity ID in all previous versions. The new composite ID is added
         * to optimize the extra cost of index for storage like BanyanDB.
         *
         * See {@link DefinedByField#idxOfCompositeID()}
         *
         * Notice, a virtual column could be both ID and a part of composite ID.
         * See {@link ServiceInstance}. Its ID is entity_id, meanwhile its composite IDs are (service_id, entity_id).
         *
         * @since 9.5.0
         */
        int idxOfCompositeID() default -1;

        /**
         * Define column length, only effective when the type is String.
         */
        int length() default 512;
    }
}
