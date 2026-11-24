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

package org.apache.skywalking.oap.server.admin.dsl.debugging.session;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The records one node captured at or after a client's point, in capture order, within a page's bounds.
 */
@Getter
@RequiredArgsConstructor
public final class CapturePage {
    private final List<ExecutionRecord> records;
    /**
     * The capture time this page holds every record of the node before: that of the first record the page's bounds
     * left out; else, when the recorder reached its cap and captures no more, {@link CaptureTime#LATEST}; else the
     * node's clock when it was read, which every record it captures later is at or after.
     */
    private final CaptureTime completeBefore;
    /**
     * Whether the recorder had reached its cap, read with the records, so a page that says so holds every record up
     * to its point.
     */
    private final boolean captured;
    /**
     * The size of the node's whole capture, read with the records.
     */
    private final long totalBytes;
}
