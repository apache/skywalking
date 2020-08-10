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

    public ScopeDefaultColumn(String fieldName, String columnName, Class<?> type, boolean isID, int length) {
        this.fieldName = fieldName;
        this.columnName = columnName;
        this.type = type;
        this.isID = isID;
        this.length = length;
    }

    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface DefinedByField {
        String columnName();

        /**
         * Dynamic active means this column is only activated through core setting explicitly.
         *
         * @return
         */
        boolean requireDynamicActive() default false;

        /**
         * Define column length, only effective when the type is String.
         */
        int length() default 256;
    }

    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface VirtualColumnDefinition {
        String fieldName();

        String columnName();

        Class type();

        boolean isID() default false;

        /**
         * Define column length, only effective when the type is String.
         */
        int length() default 512;
    }
}
