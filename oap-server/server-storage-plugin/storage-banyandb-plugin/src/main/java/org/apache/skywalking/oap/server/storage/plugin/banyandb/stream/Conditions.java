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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.library.banyandb.v1.client.Value;
import org.apache.skywalking.library.banyandb.v1.client.metadata.Serializable;

/**
 * Fluent builder for the body of a BydbQL query — the {@code WHERE ... ORDER BY ... LIMIT ...}
 * fragment appended after the {@code SELECT ... FROM ...} projection. Each condition method
 * appends both the QL text (with the correct {@code WHERE}/{@code AND} connector and {@code ?}
 * placeholder) and the bound parameter, so callers write one line per condition instead of the
 * two-line "append fragment + params.add" idiom. Mirrors the equality/match/in vocabulary of the
 * previous typed query-builder API. Not thread-safe; build one instance per query.
 */
public final class Conditions {
    private final StringBuilder ql = new StringBuilder();
    private final List<Serializable<BanyandbModel.TagValue>> params = new ArrayList<>();
    private final boolean groupMode;

    private Conditions(boolean groupMode) {
        this.groupMode = groupMode;
    }

    public static Conditions create() {
        return new Conditions(false);
    }

    /**
     * A condition group for use inside {@link #or(List)}: its conditions are joined by {@code AND} with no
     * leading {@code WHERE}, so groups can be OR'd together into {@code WHERE (a AND b) OR (c AND d)}.
     *
     * @return a new group builder
     */
    public static Conditions group() {
        return new Conditions(true);
    }

    public Conditions eq(String column, long value) {
        return condition(column, " = ?", Value.longTagValue(value));
    }

    public Conditions eq(String column, String value) {
        return condition(column, " = ?", Value.stringTagValue(value));
    }

    public Conditions ne(String column, String value) {
        return condition(column, " != ?", Value.stringTagValue(value));
    }

    public Conditions ne(String column, long value) {
        return condition(column, " != ?", Value.longTagValue(value));
    }

    public Conditions gte(String column, long value) {
        return condition(column, " >= ?", Value.longTagValue(value));
    }

    public Conditions gt(String column, long value) {
        return condition(column, " > ?", Value.longTagValue(value));
    }

    public Conditions lte(String column, long value) {
        return condition(column, " <= ?", Value.longTagValue(value));
    }

    public Conditions in(String column, List<String> values) {
        return condition(column, " IN (?)", Value.stringArrayTagValue(values));
    }

    public Conditions match(String column, String value) {
        return condition(column, " MATCH(?)", Value.stringTagValue(value));
    }

    /**
     * MATCH with an explicit analyzer and logical operator, e.g. {@code name MATCH(?, 'url', 'AND')}.
     * The analyzer and operator are QL literals; only the match text is a bound {@code ?} parameter.
     *
     * @param column   the column to match
     * @param analyzer the analyzer name (e.g. {@code url}), emitted as a single-quoted literal
     * @param logicalOp the logical operator (e.g. {@code AND}), emitted as a single-quoted literal
     * @param value    the match text, bound to the {@code ?} placeholder
     * @return this builder
     */
    public Conditions match(String column, String analyzer, String logicalOp, String value) {
        return condition(column, " MATCH(?, '" + analyzer + "', '" + logicalOp + "')", Value.stringTagValue(value));
    }

    public Conditions having(String column, List<String> values) {
        return condition(column, " HAVING (?)", Value.stringArrayTagValue(values));
    }

    public Conditions orderByDesc() {
        ql.append(" ORDER BY DESC");
        return this;
    }

    public Conditions orderByAsc() {
        ql.append(" ORDER BY ASC");
        return this;
    }

    public Conditions orderByDesc(String column) {
        ql.append(" ORDER BY ").append(column).append(" DESC");
        return this;
    }

    /**
     * {@code ORDER BY <column> <direction>} where the direction (e.g. {@code ASC}/{@code DESC}) is a
     * QL literal resolved by the caller — for queries whose sort direction is dynamic.
     *
     * @param column    the column to order by
     * @param direction the sort direction literal ({@code ASC} or {@code DESC})
     * @return this builder
     */
    public Conditions orderBy(String column, String direction) {
        ql.append(" ORDER BY ").append(column).append(" ").append(direction);
        return this;
    }

    /**
     * {@code GROUP BY col1, col2, ...} — appended after the WHERE clause. No parameters.
     *
     * @param columns the group-by columns, in order
     * @return this builder
     */
    public Conditions groupBy(String... columns) {
        ql.append(" GROUP BY ").append(String.join(", ", columns));
        return this;
    }

    public Conditions limit(long value) {
        ql.append(" LIMIT ?");
        params.add(Value.longTagValue(value));
        return this;
    }

    public Conditions offset(long value) {
        ql.append(" OFFSET ?");
        params.add(Value.longTagValue(value));
        return this;
    }

    /**
     * @return the assembled QL body ({@code WHERE ... ORDER BY ... LIMIT ...}), to be appended
     * after the {@code SELECT ... FROM ...} projection.
     */
    public String buildQl() {
        return ql.toString();
    }

    /**
     * @return the bound parameters, in the order their {@code ?} placeholders appear in {@link #buildQl()}.
     */
    public List<Serializable<BanyandbModel.TagValue>> params() {
        return params;
    }

    /**
     * Combine condition {@link #group() groups} with OR: {@code WHERE (g1) OR (g2) OR ...}. A single non-empty
     * group is emitted without parentheses ({@code WHERE g1}); empty groups are skipped; if every group is empty
     * no clause is emitted. Params are appended in group order.
     *
     * @param groups the condition groups, each built via {@link #group()}
     * @return this builder
     */
    public Conditions or(List<Conditions> groups) {
        final List<Conditions> nonEmpty = new ArrayList<>();
        for (final Conditions g : groups) {
            if (g.ql.length() > 0) {
                nonEmpty.add(g);
            }
        }
        if (nonEmpty.isEmpty()) {
            return this;
        }
        ql.append(ql.length() == 0 ? " WHERE " : " AND ");
        if (nonEmpty.size() == 1) {
            ql.append(nonEmpty.get(0).ql);
            params.addAll(nonEmpty.get(0).params);
        } else {
            for (int i = 0; i < nonEmpty.size(); i++) {
                if (i > 0) {
                    ql.append(" OR ");
                }
                ql.append("(").append(nonEmpty.get(i).ql).append(")");
                params.addAll(nonEmpty.get(i).params);
            }
        }
        return this;
    }

    private Conditions condition(String column, String operator, Serializable<BanyandbModel.TagValue> value) {
        final String connector = ql.length() == 0 ? (groupMode ? "" : " WHERE ") : " AND ";
        ql.append(connector).append(column).append(operator);
        params.add(value);
        return this;
    }
}
