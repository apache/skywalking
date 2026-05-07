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
import lombok.RequiredArgsConstructor;

/**
 * Result of a {@link DebugSessionRegistry#installWithId} call. Lets the
 * cluster handler distinguish a freshly bound recorder from an idempotent
 * replay (same sessionId, already bound) without the hack of comparing the
 * returned session's id against the request's. The local POST handler
 * never observes {@link Status#ALREADY_INSTALLED} since it always mints a
 * fresh sessionId; this outcome shape is shared across both paths so the
 * code is uniform.
 */
@Getter
@RequiredArgsConstructor
public final class InstallOutcome {
    public enum Status {
        /** Recorder bound to a live holder on this node. Capture is active. */
        INSTALLED,
        /** A session with the requested sessionId was already bound. No-op. */
        ALREADY_INSTALLED,
        /** No live holder for the rule on this node. Treated as best-effort. */
        NOT_LOCAL,
        /**
         * Active-session ceiling reached on this node — admin surface refuses
         * to bind another recorder. Caller surfaces this as 429 too_many_sessions.
         */
        TOO_MANY_SESSIONS
    }

    public static final InstallOutcome NOT_LOCAL_OUTCOME = new InstallOutcome(Status.NOT_LOCAL, null);
    public static final InstallOutcome TOO_MANY_SESSIONS_OUTCOME =
        new InstallOutcome(Status.TOO_MANY_SESSIONS, null);

    private final Status status;
    private final DebugSession session;

    public boolean isInstalled() {
        return status == Status.INSTALLED;
    }
}
