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

/**
 * SQLDatabase annotation is a holder including all annotations for SQL-based RDBMS storage
 *
 * @since 9.1.0
 */
public @interface SQLDatabase {
    /**
     * QueryIndex defines the unified index is required in the query stage. This works only the storage supports this kind
     * of index model. Mostly, work for the typical relational database, such as MySQL, TiDB.
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(MultipleQueryUnifiedIndex.class)
    @interface QueryUnifiedIndex {

        /**
         * @return list of other column should be add into the unified index.
         */
        String[] withColumns();
    }

    /**
     * The support of the multiple {@link QueryUnifiedIndex}s on one field.
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface MultipleQueryUnifiedIndex {
        QueryUnifiedIndex[] value();
    }

    /**
     *  Support create additional tables from a model.
     *  The typical use is: when need to storage a `List` field, we can transform it to another table as row set.
     *  Notice:
     *    This feature only support `Record` type.
     *    An additional table only supports one list-type field.
     *    Create `MultiColumnsIndex` on the additional table only when it contains all need columns.
     */
    @interface AdditionalEntity {
        @Target({ElementType.FIELD})
        @Retention(RetentionPolicy.RUNTIME)
        @interface OnlyAdditional {
            String[] additionalTables();
        }

        @Target({ElementType.FIELD})
        @Retention(RetentionPolicy.RUNTIME)
        @interface OriginAndAdditional {
            String[] additionalTables();
        }
    }
}
