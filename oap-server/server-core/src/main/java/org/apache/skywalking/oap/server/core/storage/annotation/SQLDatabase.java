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
     * {@code CompositeIndex} defines the composite index required in the query stage.
     * This works only when the storage supports this kind of index model, mostly,
     * work for the typical relational database, such as MySQL, TiDB.
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(CompositeIndices.class)
    @interface CompositeIndex {

        /**
         * @return list of other column should be add into the unified index.
         */
        String[] withColumns();
    }

    /**
     * The support of the multiple {@link CompositeIndex}s on one field.
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface CompositeIndices {
        CompositeIndex[] value();
    }

    /**
     * Support create additional tables from a model.<br>
     * <p>
     * Notice:
     * <ul>
     * <li>This feature only support `Record` type.
     * <li>An additional table only supports one list-type field.
     * <li>Create `MultiColumnsIndex` on the additional table only when it contains all need columns.
     * </ul>
     * <p>
     * The typical use is: when need to storage a `List` field, we can transform it to another table as row set.<br>
     * For example in SegmentRecord#tags create an additional table:
     * <pre>
     *     {@code @SQLDatabase.AdditionalEntity(additionalTables = {ADDITIONAL_TAG_TABLE})}
     *     {@code private List<String> tags;}
     * </pre>
     * <p>
     * In H2TraceQueryDAO#queryBasicTraces query tags as condition from this additional table, could build sql like this:
     * <pre>{@code
     *         if (!CollectionUtils.isEmpty(tags)) {
     *             for (int i = 0; i < tags.size(); i++) {
     *                 sql.append(" inner join ").append(SegmentRecord.ADDITIONAL_TAG_TABLE).append(" ");
     *                 sql.append(SegmentRecord.ADDITIONAL_TAG_TABLE + i);
     *                 sql.append(" on ").append(SegmentRecord.INDEX_NAME).append(".").append(ID_COLUMN).append(" = ");
     *                 sql.append(SegmentRecord.ADDITIONAL_TAG_TABLE + i).append(".").append(ID_COLUMN);
     *             }
     *         }
     *         ...
     *         if (CollectionUtils.isNotEmpty(tags)) {
     *             for (int i = 0; i < tags.size(); i++) {
     *                 final int foundIdx = searchableTagKeys.indexOf(tags.get(i).getKey());
     *                 if (foundIdx > -1) {
     *                     sql.append(" and ").append(SegmentRecord.ADDITIONAL_TAG_TABLE + i).append(".");
     *                     sql.append(SegmentRecord.TAGS).append(" = ?");
     *                     parameters.add(tags.get(i).toString());
     *                 } else {
     *                     //If the tag is not searchable, but is required, then don't need to run the real query.
     *                     return new TraceBrief();
     *                 }
     *             }
     *         }
     * }</pre>
     * <p>
     * <ul>
     * <li>If no tags condition, only query segment table, the SQL should be: select
     * column1, column2 ... from segment where 1=1 and column1=xx ...
     *
     * <li> If 1 tag condition, query both segment and segment_tag tables, the SQL should be: select column1, column2 ...
     * from segment inner join segment_tag segment_tag0 on segment.id=segment_tag0.id where 1=1 and colunm1=xx ... and
     * segment_tag0=tagString0
     *
     * <li> If 2 or more tags condition, query both segment and segment_tag tables, the SQL should be: select column1,
     * column2 ... from segment inner join segment_tag segment_tag0 on segment.id=segment_tag0.id inner join segment_tag
     * segment_tag1 on segment.id=segment_tag1.id ... where 1=1 and column1=xx ... and segment_tag0=tagString0 and
     * segment_tag1=tagString1 ...
     * </ul>
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface AdditionalEntity {
        String[] additionalTables();
        boolean reserveOriginalColumns() default false;
    }

    /**
     * Support add an extra column from the parent classes as a column of the additional table.
     * This column would be created in both the primary and additional tables.
     * Notice: This annotation should be declared on the leaf subclasses.
     */
    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(MultipleExtraColumn4AdditionalEntity.class)
    @interface ExtraColumn4AdditionalEntity {
        String additionalTable();
        String parentColumn();
    }

    /**
     * The support of the multiple {@link ExtraColumn4AdditionalEntity}s on the class.
     */
    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface MultipleExtraColumn4AdditionalEntity {
        ExtraColumn4AdditionalEntity[] value();
    }
}
