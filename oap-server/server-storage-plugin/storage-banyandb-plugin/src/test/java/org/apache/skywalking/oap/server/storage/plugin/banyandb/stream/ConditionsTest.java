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

import java.util.List;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.library.banyandb.v1.client.metadata.Serializable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConditionsTest {

    private static String str(Serializable<BanyandbModel.TagValue> param) {
        return param.serialize().getStr().getValue();
    }

    private static long lng(Serializable<BanyandbModel.TagValue> param) {
        return param.serialize().getInt().getValue();
    }

    @Test
    public void emptyProducesNoClauseAndNoParams() {
        final Conditions where = Conditions.create();
        assertEquals("", where.buildQl());
        assertTrue(where.params().isEmpty());
    }

    @Test
    public void firstPredicateUsesWhereConnector() {
        final Conditions where = Conditions.create().eq("a", "x");
        assertEquals(" WHERE a = ?", where.buildQl());
        assertEquals(1, where.params().size());
        assertEquals("x", str(where.params().get(0)));
    }

    @Test
    public void secondPredicateUsesAndConnectorAndParamsKeepOrder() {
        final Conditions where = Conditions.create().eq("a", "x").gte("b", 5L);
        assertEquals(" WHERE a = ? AND b >= ?", where.buildQl());
        final List<Serializable<BanyandbModel.TagValue>> params = where.params();
        assertEquals(2, params.size());
        assertEquals("x", str(params.get(0)));
        assertEquals(5L, lng(params.get(1)));
    }

    @Test
    public void comparisonOperatorsRenderExpectedSymbols() {
        assertEquals(" WHERE a != ?", Conditions.create().ne("a", "x").buildQl());
        assertEquals(" WHERE a > ?", Conditions.create().gt("a", 1L).buildQl());
        assertEquals(" WHERE a >= ?", Conditions.create().gte("a", 1L).buildQl());
        assertEquals(" WHERE a <= ?", Conditions.create().lte("a", 1L).buildQl());
    }

    @Test
    public void inMatchAndHavingRenderExpectedClauses() {
        assertEquals(" WHERE a IN (?)", Conditions.create().in("a", List.of("x", "y")).buildQl());
        assertEquals(" WHERE a MATCH(?)", Conditions.create().match("a", "kw").buildQl());
        assertEquals(" WHERE a MATCH(?, 'url', 'AND')",
                     Conditions.create().match("a", "url", "AND", "kw").buildQl());
        assertEquals(" WHERE a HAVING (?)", Conditions.create().having("a", List.of("x")).buildQl());
    }

    @Test
    public void inBindsAStringArrayParam() {
        final Conditions where = Conditions.create().in("a", List.of("x", "y"));
        assertEquals(1, where.params().size());
        assertEquals(List.of("x", "y"), where.params().get(0).serialize().getStrArray().getValueList());
    }

    @Test
    public void tailClausesComposeInOrderWithParams() {
        final Conditions where = Conditions.create()
                .eq("a", "x")
                .groupBy("g1", "g2")
                .orderByDesc("t")
                .limit(10)
                .offset(20);
        assertEquals(" WHERE a = ? GROUP BY g1, g2 ORDER BY t DESC LIMIT ? OFFSET ?", where.buildQl());
        final List<Serializable<BanyandbModel.TagValue>> params = where.params();
        assertEquals(3, params.size());
        assertEquals("x", str(params.get(0)));
        assertEquals(10L, lng(params.get(1)));
        assertEquals(20L, lng(params.get(2)));
    }

    @Test
    public void directionOnlyAndColumnOrderBy() {
        assertEquals(" ORDER BY DESC", Conditions.create().orderByDesc().buildQl());
        assertEquals(" ORDER BY ASC", Conditions.create().orderByAsc().buildQl());
        assertEquals(" ORDER BY t ASC", Conditions.create().orderBy("t", "ASC").buildQl());
    }

    @Test
    public void groupModeOmitsLeadingWhere() {
        final Conditions group = Conditions.group().eq("a", "x").eq("b", "y");
        assertEquals("a = ? AND b = ?", group.buildQl());
    }

    @Test
    public void orWithNoGroupsEmitsNothing() {
        final Conditions where = Conditions.create().or(List.of());
        assertEquals("", where.buildQl());
        assertTrue(where.params().isEmpty());
    }

    @Test
    public void orWithOnlyEmptyGroupsEmitsNothing() {
        final Conditions where = Conditions.create().or(List.of(Conditions.group(), Conditions.group()));
        assertEquals("", where.buildQl());
        assertTrue(where.params().isEmpty());
    }

    @Test
    public void orWithSingleNonEmptyGroupHasNoParentheses() {
        final Conditions where = Conditions.create().or(List.of(
                Conditions.group().in("a", List.of("x"))));
        assertEquals(" WHERE a IN (?)", where.buildQl());
        assertEquals(1, where.params().size());
    }

    @Test
    public void orWithMultipleGroupsWrapsInParenthesesAndOrdersParams() {
        final Conditions where = Conditions.create().or(List.of(
                Conditions.group().eq("a", "x"),
                Conditions.group().eq("b", "y")));
        assertEquals(" WHERE (a = ?) OR (b = ?)", where.buildQl());
        final List<Serializable<BanyandbModel.TagValue>> params = where.params();
        assertEquals(2, params.size());
        assertEquals("x", str(params.get(0)));
        assertEquals("y", str(params.get(1)));
    }

    @Test
    public void orSkipsEmptyGroupsAndCollapsesToSingle() {
        final Conditions where = Conditions.create().or(List.of(
                Conditions.group(),
                Conditions.group().eq("a", "x"),
                Conditions.group()));
        assertEquals(" WHERE a = ?", where.buildQl());
        assertEquals(1, where.params().size());
    }
}
