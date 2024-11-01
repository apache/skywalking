/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.skywalking.oap.server.library.jfr.parser.type.event;

import org.apache.skywalking.oap.server.library.jfr.parser.type.JfrReader;

public class ObjectCount extends Event {
    public final int gcId;
    public final int classId;
    public final long count;
    public final long totalSize;

    public ObjectCount(JfrReader jfr) {
        super(jfr.getVarlong(), 0, 0);
        this.gcId = jfr.getVarint();
        this.classId = jfr.getVarint();
        this.count = jfr.getVarlong();
        this.totalSize = jfr.getVarlong();
    }
}
