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

package org.apache.skywalking.oap.server.core.query.input;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * A single constraint on the alarm record's {@code id0} / {@code id1} columns.
 * Within a constraint the two predicates AND together; across a list of
 * constraints (e.g., one per input {@link Entity}) the constraints OR
 * together. A {@code null} value means "no constraint on that column".
 *
 * <p>Used by the alarm query DAO to translate a list of {@link Entity}
 * filters into a precise storage predicate that distinguishes non-relation
 * entities (one ID — match as primary OR as relation-destination) from
 * relation entities (two IDs — exact source / destination match).
 *
 * @since 11.0.0
 */
@AllArgsConstructor
@Getter
@ToString
public class EntityIdConstraint {
    /**
     * Required value of {@code AlarmRecord.id0}. {@code null} means the
     * id0 column is unconstrained by this entry.
     */
    private final String id0;

    /**
     * Required value of {@code AlarmRecord.id1}. {@code null} means the
     * id1 column is unconstrained by this entry.
     */
    private final String id1;
}
