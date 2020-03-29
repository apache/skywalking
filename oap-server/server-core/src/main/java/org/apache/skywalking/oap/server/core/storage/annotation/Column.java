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
import org.apache.skywalking.oap.server.core.query.sql.Function;
import org.apache.skywalking.oap.server.core.storage.model.IModelOverride;

/**
 * Data column of all persistent entity.
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Column {
    /**
     * column name in the storage. Most of the storage will keep the name consistently. But in same cases, this name
     * could be a keyword, then, the implementation will use {@link IModelOverride} to replace the column name.
     */
    String columnName();

    /**
     * The column value is used in metrics value query.
     */
    boolean isValue() default false;

    /**
     * The function is used in aggregation query.
     */
    Function function() default Function.None;

    /**
     * Match query means using analyzer(if storage have) to do key word match query.
     */
    boolean matchQuery() default false;

    /**
     * The column is just saved, never used in query.
     */
    boolean storageOnly() default false;

    /**
     * @return the length of this column, this is only for {@link String} column. The usage of this depends on the
     * storage implementation.
     * @since 7.1.0
     */
    int length() default 200;
}
