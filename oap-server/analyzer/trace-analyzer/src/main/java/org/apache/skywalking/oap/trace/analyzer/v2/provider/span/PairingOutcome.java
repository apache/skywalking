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
 */

package org.apache.skywalking.oap.trace.analyzer.v2.provider.span;

import lombok.Getter;

/**
 * What became of a half of a call offered to {@link SpanPairing}: sent to the trace's owner, or why not. Read only by
 * a dsl-debugging capture, which shows it on the run that asked for the half.
 */
public enum PairingOutcome {
    SENT("sent"),
    /**
     * The trace falls outside {@code pairingSamplePercent}.
     */
    NOT_SAMPLED("notSampled"),
    /**
     * A callee half comes from a SERVER span only.
     */
    NOT_SERVER_SPAN("notServerSpan"),
    /**
     * A root SERVER span has no caller to pair with.
     */
    ROOT_SPAN("rootSpan"),
    /**
     * The SERVER span's parent ran in the same process, so no other service called it.
     */
    PARENT_LOCAL("parentLocal"),
    /**
     * A caller half comes from a CLIENT span, or a SERVER span that passed its context on.
     */
    NOT_CALLER_KIND("notCallerKind"),
    /**
     * The rule named no peer.
     */
    NO_PEER("noPeer");

    @Getter
    private final String code;

    PairingOutcome(final String code) {
        this.code = code;
    }
}
