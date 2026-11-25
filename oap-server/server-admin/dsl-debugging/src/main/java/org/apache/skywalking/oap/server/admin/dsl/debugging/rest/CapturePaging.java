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

package org.apache.skywalking.oap.server.admin.dsl.debugging.rest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.CaptureTime;

/**
 * A page of a session across the nodes. Each node sends the records it captured at or after the request's point, and
 * the response keeps those captured before the point every answering node is complete before, which the next request
 * starts from, on whichever node it lands. Capture times are compared, never computed with.
 */
final class CapturePaging {
    static final int MAX_PAGE_SIZE = 1000;
    /**
     * The bytes of one response, from every node, which share it evenly. A wide bound, so the page size is what a
     * read meets; a node streams its share to the serving node one record a message.
     */
    static final long RESPONSE_BYTES = 100L * 1024 * 1024;

    private CapturePaging() {
    }

    /**
     * A record as the response shows it, with its capture time and its size.
     */
    @RequiredArgsConstructor
    static final class Entry {
        private final CaptureTime capturedAt;
        private final long bytes;
        private final JsonObject json;
    }

    /**
     * One node's page as it arrived.
     */
    @RequiredArgsConstructor
    static final class NodePage {
        private final List<Entry> entries;
        /**
         * The capture time the page holds every record of the node before.
         */
        private final CaptureTime completeBefore;
    }

    /**
     * The point the next request starts from: the earliest one a node's page is complete before, so a node whose page
     * was cut short, or whose clock runs behind while it still captures, holds the others' later records for the next
     * response (a node that reached its cap is complete before {@link CaptureTime#LATEST} and holds back none); then the
     * time of the first record left out, should the response's records pass the page size or {@link #RESPONSE_BYTES},
     * between two capture times, so records of one time are not split.
     *
     * @return the point, never before the request's; the request's when no node answered
     */
    static CaptureTime nextSince(final List<NodePage> pages, final CaptureTime since, final int pageSize) {
        if (pages.isEmpty()) {
            return since;
        }
        CaptureTime bound = null;
        for (final NodePage page : pages) {
            if (bound == null || page.completeBefore.compareTo(bound) < 0) {
                bound = page.completeBefore;
            }
        }
        final List<Entry> candidates = new ArrayList<>();
        for (final NodePage page : pages) {
            for (final Entry entry : page.entries) {
                if (within(entry, since, bound)) {
                    candidates.add(entry);
                }
            }
        }
        candidates.sort(Comparator.comparing(entry -> entry.capturedAt));
        CaptureTime next = bound;
        int count = 0;
        long bytes = 0;
        for (int i = 0; i < candidates.size(); i++) {
            final Entry entry = candidates.get(i);
            if (count > 0 && !entry.capturedAt.equals(candidates.get(i - 1).capturedAt)
                && (count >= pageSize || bytes + entry.bytes > RESPONSE_BYTES)) {
                next = entry.capturedAt;
                break;
            }
            count++;
            bytes += entry.bytes;
        }
        return next.compareTo(since) < 0 ? since : next;
    }

    /**
     * The pages the point is taken over. A node that did not answer may hold the session, so an empty page bounded by
     * the serving node's clock stands in for it: its records are skipped up to about now only, never up to a capped
     * node's latest time. When no node holding the session answered, none stands in, so the point stays where the
     * client gave it and the next read starts there again.
     *
     * @param answered   the pages of the nodes holding the session that answered
     * @param unanswered whether a node that may hold the session did not answer
     * @param servedAt   the serving node's clock
     * @return the pages to take the point over
     */
    static List<NodePage> withUnanswered(final List<NodePage> answered, final boolean unanswered,
                                         final CaptureTime servedAt) {
        if (!unanswered || answered.isEmpty()) {
            return answered;
        }
        final List<NodePage> pages = new ArrayList<>(answered);
        pages.add(new NodePage(new ArrayList<>(), servedAt));
        return pages;
    }

    /**
     * @return whether the capture is read through: every node is complete before {@link CaptureTime#LATEST}, having
     * reached its cap, and the response was not cut; the point alone is no proof, as it never moves back
     */
    static boolean readThrough(final List<NodePage> pages, final CaptureTime next) {
        if (pages.isEmpty() || !CaptureTime.LATEST.equals(next)) {
            return false;
        }
        for (final NodePage page : pages) {
            if (!CaptureTime.LATEST.equals(page.completeBefore)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return the node's records the response shows: those at or after the request's point and before the next
     */
    static JsonArray kept(final NodePage page, final CaptureTime since, final CaptureTime next) {
        final JsonArray records = new JsonArray();
        for (final Entry entry : page.entries) {
            if (within(entry, since, next)) {
                records.add(entry.json);
            }
        }
        return records;
    }

    private static boolean within(final Entry entry, final CaptureTime since, final CaptureTime before) {
        return entry.capturedAt.compareTo(since) >= 0 && entry.capturedAt.compareTo(before) < 0;
    }
}
