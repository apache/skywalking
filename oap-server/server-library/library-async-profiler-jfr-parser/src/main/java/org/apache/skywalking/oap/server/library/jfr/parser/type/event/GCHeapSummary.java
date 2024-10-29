/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

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
