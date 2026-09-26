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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The order of what happened, as the Sessionizer lists it: by position inside one lane, a stream or a workflow run,
 * and by time across lanes. A position means nothing across lanes, since a child's file can land before its parent's.
 * An id decides only between items that one record supports.
 */
final class Order {
    private Order() {
    }

    /**
     * Where an item happened: the lane its record landed in, the record's position there, and the time the record
     * carries, 0 when it carries none.
     */
    static final class Point {
        static final Point NOWHERE = new Point("", 0, 0, -1, 0);

        final String lane;
        final long seq;
        final long row;
        /** -1 when the item stands on the whole record. */
        final long block;
        final long at;

        Point(final String lane, final long seq, final long row, final long block, final long at) {
            this.lane = lane;
            this.seq = seq;
            this.row = row;
            this.block = block;
            this.at = at;
        }

        boolean isLanded() {
            return seq != 0;
        }
    }

    /** Orders two points by position alone, which is meaningful inside one lane. */
    static int comparePositions(final Point a, final Point b) {
        if (a.seq != b.seq) {
            return Long.compare(a.seq, b.seq);
        }
        if (a.row != b.row) {
            return Long.compare(a.row, b.row);
        }
        return Long.compare(a.block, b.block);
    }

    /**
     * @return the earliest of several points by the rule that orders items: the first position inside each lane, then
     * the earliest time across lanes; nowhere when none is landed
     */
    static Point earliest(final List<Point> points) {
        final Map<String, Point> first = new LinkedHashMap<>();
        for (final Point p : points) {
            if (p.isLanded()) {
                first.merge(p.lane, p, (held, next) -> comparePositions(next, held) < 0 ? next : held);
            }
        }
        Point out = Point.NOWHERE;
        for (final Point p : first.values()) {
            if (!out.isLanded() || earlier(p, out)) {
                out = p;
            }
        }
        return out;
    }

    /**
     * Sorts items the way they happened. It is a merge, not one comparison: each lane's items are put in position
     * order, then the lanes are merged by taking, each time, the lane whose next item is earliest, a timed item
     * before an untimed one. A single comparison that decides some pairs by position and others by time is not
     * transitive, and a sort over it is undefined. Two lanes whose next items share a time are decided by position,
     * which differs between lanes, so the result is the same on every read.
     *
     * @param tie decides between items at the very same position; items with no position at all come last, in this
     *            order
     */
    static <T> List<T> inOrder(final List<T> items, final Function<T, Point> pointOf, final Comparator<T> tie) {
        final Map<String, List<T>> byLane = new LinkedHashMap<>();
        final List<T> nowhere = new ArrayList<>();
        for (final T it : items) {
            final Point p = pointOf.apply(it);
            if (p.isLanded()) {
                byLane.computeIfAbsent(p.lane, k -> new ArrayList<>()).add(it);
            } else {
                nowhere.add(it);
            }
        }
        final List<List<T>> lanes = new ArrayList<>(byLane.values());
        for (final List<T> lane : lanes) {
            lane.sort(Comparator.comparing(pointOf, Order::comparePositions).thenComparing(tie));
        }
        final List<T> out = new ArrayList<>(items.size());
        final int[] next = new int[lanes.size()];
        while (out.size() < items.size() - nowhere.size()) {
            int best = -1;
            Point bestAt = null;
            for (int i = 0; i < lanes.size(); i++) {
                if (next[i] >= lanes.get(i).size()) {
                    continue;
                }
                final Point p = pointOf.apply(lanes.get(i).get(next[i]));
                if (best < 0 || earlier(p, bestAt)) {
                    best = i;
                    bestAt = p;
                }
            }
            out.add(lanes.get(best).get(next[best]++));
        }
        nowhere.sort(tie);
        out.addAll(nowhere);
        return out;
    }

    /** Decides between the next items of two lanes: a timed one first, then the earlier time, then the position. */
    private static boolean earlier(final Point a, final Point b) {
        if ((a.at != 0) != (b.at != 0)) {
            return a.at != 0;
        }
        if (a.at != b.at) {
            return a.at < b.at;
        }
        return comparePositions(a, b) < 0;
    }
}
