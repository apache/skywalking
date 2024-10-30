/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.skywalking.oap.server.library.jfr.parser.type;

public class ClassRef {
    public final long name;

    public ClassRef(long name) {
        this.name = name;
    }
}
