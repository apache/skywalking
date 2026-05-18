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

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.type.Pagination;

/**
 * Filter condition for the new {@code queryAlarms} GraphQL query. Mirrors the
 * {@code AlarmQueryCondition} input type from {@code alarm.graphqls}.
 *
 * <p>Within each list-typed field values OR together (set union); across
 * fields the predicates AND together. {@link #duration} and {@link #paging}
 * are required; everything else is optional — a null / empty list disables
 * that filter.
 *
 * @since 11.0.0
 */
@Setter
@Getter
@ToString
public class AlarmQueryCondition {
    private Duration duration;
    private Pagination paging;
    /**
     * Each entity is resolved at query time to one or two IDs (one for
     * non-relation scopes, source + dest for relations) and matched against
     * the alarm record's id0 OR id1 columns. Across the list, matches OR
     * together.
     */
    private List<Entity> entities;
    /**
     * Single-layer filter. Matches the alarm record's stored layer column
     * exactly — alarms persist one layer per row (the first entry of the
     * entity's resolved layer list).
     */
    private String layer;
    /**
     * Filter by the alarm rule(s) that fired the alarm. Matches the alarm
     * record's rule_name column exactly.
     */
    private List<String> ruleNames;
    /**
     * Phrase match on the alarm_message text. Same semantics as
     * {@code getAlarm.keyword}.
     */
    private String keyword;
    /**
     * Searchable-tag filter. Tag keys must be in the OAP backend's
     * {@code searchableAlarmTags} config; otherwise the query returns no rows.
     */
    private List<Tag> tags;
}
