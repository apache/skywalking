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

package org.apache.skywalking.oap.server.ai.agent.conversation.view;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OrderTest {
    private static final class Item {
        final String name;
        final Order.Point point;

        Item(final String name, final Order.Point point) {
            this.name = name;
            this.point = point;
        }
    }

    private static Item at(final String name, final String lane, final long seq, final long row, final long time) {
        return new Item(name, new Order.Point(lane, seq, row, -1, time));
    }

    /**
     * A position orders records inside one lane, and time orders lanes against each other: a child's file lands after
     * its parent's, so by sequence all of its records would follow, though it ran in the middle. Inside a lane the
     * position holds even where the times disagree, a timed item comes before an untimed one when lanes are merged,
     * and an item with no position at all comes last. The same items and the same order as the Sessionizer's
     * TestInOrderMergesLanesByTime.
     */
    @Test
    public void lanesAreMergedByTime() {
        final List<Item> items = new ArrayList<>(Arrays.asList(
            at("child-2", "stream/c1", 5, 2, 25),
            at("main-3", "stream/main", 1, 3, 30),
            at("main-1", "stream/main", 1, 1, 10),
            new Item("nowhere", Order.Point.NOWHERE),
            at("child-1", "stream/c1", 5, 1, 20),
            // an earlier time than main-1, but after it in the stream
            at("main-2", "stream/main", 1, 2, 5),
            // untimed: after every timed lane head
            at("run-1", "run/r1", 9, 1, 0),
            // two lane heads of one time: the earlier position first
            at("tie-b", "stream/b", 7, 1, 40),
            at("tie-a", "stream/a", 6, 1, 40)));
        for (int i = 0; i < 20; i++) {
            final List<String> names = new ArrayList<>();
            for (final Item x : Order.inOrder(items, x -> x.point, Comparator.comparing(x -> x.name))) {
                names.add(x.name);
            }
            assertEquals(Arrays.asList("main-1", "main-2", "child-1", "child-2", "main-3", "tie-a", "tie-b", "run-1", "nowhere"),
                         names);
            // the input order must not matter
            Collections.rotate(items, 3);
        }
    }
}
