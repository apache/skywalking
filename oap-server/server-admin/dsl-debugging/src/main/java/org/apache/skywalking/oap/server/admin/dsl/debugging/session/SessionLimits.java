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

import lombok.Getter;

/**
 * Per-session quotas + behaviour switches passed from the install API
 * down to the recorder factory.
 *
 * <p>Quotas: maximum number of records to retain ({@link #recordCap}),
 * and the wall-clock retention window ({@link #retentionMillis}). No
 * memory-byte ceiling — the recorder serialises records as JSON at
 * probe time and stops appending once the count cap is hit; payload
 * size is reported in REST responses for operator visibility but does
 * not gate.
 *
 * <p>Behaviour switches: {@link #granularity} selects per-statement vs
 * per-block capture for DSLs that distinguish (LAL today). Recorders
 * that don't differentiate ignore it.
 */
@Getter
public final class SessionLimits {

    /**
     * Hard upper bound on per-session retained records. The recorder appends
     * one record per probe pass and stops once the count reaches the cap.
     * Payloads can be ~10 KiB for richly-tagged MAL/LAL flows, so 100 records
     * caps the per-session heap footprint at ~1 MiB and keeps the rendered
     * UI page readable — operators inspect a handful of executions, not a
     * paginated firehose, so a small cap is the right product shape.
     */
    public static final int MAX_RECORD_CAP = 100;

    /**
     * Hard upper bound on the per-session retention window (1 hour). Sessions
     * are operator-driven and short-lived by design; a runaway retention would
     * keep payload JSON pinned in heap long after the operator has left.
     */
    public static final long MAX_RETENTION_MILLIS = 60L * 60 * 1000;

    public static final SessionLimits DEFAULT =
        new SessionLimits(MAX_RECORD_CAP, 5L * 60 * 1000, Granularity.DEFAULT);

    private final int recordCap;
    private final long retentionMillis;
    private final Granularity granularity;

    public SessionLimits(final int recordCap, final long retentionMillis,
                         final Granularity granularity) {
        if (recordCap <= 0) {
            throw new IllegalArgumentException(
                "recordCap must be > 0, got " + recordCap);
        }
        if (recordCap > MAX_RECORD_CAP) {
            throw new IllegalArgumentException(
                "recordCap " + recordCap + " exceeds hard cap " + MAX_RECORD_CAP);
        }
        if (retentionMillis <= 0) {
            throw new IllegalArgumentException(
                "retentionMillis must be > 0, got " + retentionMillis);
        }
        if (retentionMillis > MAX_RETENTION_MILLIS) {
            throw new IllegalArgumentException(
                "retentionMillis " + retentionMillis + " exceeds hard cap " + MAX_RETENTION_MILLIS);
        }
        this.recordCap = recordCap;
        this.retentionMillis = retentionMillis;
        this.granularity = granularity == null ? Granularity.DEFAULT : granularity;
    }

    /** Convenience for callers that don't override granularity. */
    public SessionLimits(final int recordCap, final long retentionMillis) {
        this(recordCap, retentionMillis, Granularity.DEFAULT);
    }
}
