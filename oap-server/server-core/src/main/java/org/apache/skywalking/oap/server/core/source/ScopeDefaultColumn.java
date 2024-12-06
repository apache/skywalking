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

import lombok.Getter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Define the default columns of source scope. These columns pass down into the persistent entity(OAL metrics entity)
 * automatically.
 */
@Getter
public class ScopeDefaultColumn {
    private String fieldName;
    private String columnName;
    private Class<?> type;
    private boolean isID;
    private int length;
    private final boolean groupByCondInTopN;
    private final boolean attribute;

    public ScopeDefaultColumn(String fieldName, String columnName, Class<?> type, boolean isID, int length, boolean groupByCondInTopN, boolean attribute) {
        this.fieldName = fieldName;
        this.columnName = columnName;
        this.type = type;
        this.isID = isID;
        this.length = length;
        this.groupByCondInTopN = groupByCondInTopN;
        this.attribute = attribute;
    }

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
         * Indicate whether this column is an attribute.
         * Attributes are optional fields, which are set by the source decorator and can be used for query conditions.
         *
         * @since 10.2.0
         */
        boolean isAttribute() default false;
    }

    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface BanyanDB {
        /**
         * Indicate whether this column is a condition for groupBy in the TopN Aggregation.
         *
         * @since 9.5.0
         * @since 10.2.0 moved out from {@link DefinedByField} to {@link BanyanDB}
         */
        boolean groupByCondInTopN() default false;
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
         * Define column length, only effective when the type is String.
         */
        int length() default 512;
    }
}
