/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.skywalking.oap.server.library.jfr.parser.type.event;

import org.apache.skywalking.oap.server.library.jfr.parser.type.JfrReader;

public class GCHeapSummary extends Event {
    public final int gcId;
    public final boolean afterGC;
    public final long committed;
    public final long reserved;
    public final long used;

    public GCHeapSummary(JfrReader jfr) {
        super(jfr.getVarlong(), 0, 0);
        this.gcId = jfr.getVarint();
        this.afterGC = jfr.getVarint() > 0;
        long start = jfr.getVarlong();
        long committedEnd = jfr.getVarlong();
        this.committed = jfr.getVarlong();
        long reservedEnd = jfr.getVarlong();
        this.reserved = jfr.getVarlong();
        this.used = jfr.getVarlong();
    }
}
