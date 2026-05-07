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

package org.apache.skywalking.oap.server.admin.dsl.debugging.cluster;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Per-peer result of a cluster RPC fan-out. Carries the typed ack
 * payload on success or a non-null transport-failure detail on
 * unreachable / timeout / refused peers — the receiving node's
 * aggregator surfaces both shapes uniformly to the REST caller.
 *
 * <p>Wrapping the result rather than letting null leak out of the
 * fan-out helper makes the aggregation side branch-free: the receiving
 * REST handler iterates outcomes, picks {@link #getAck()} when present,
 * picks {@link #getFailure()} otherwise. No null checks scattered
 * across the response builder.
 */
@Getter
@RequiredArgsConstructor
public final class PeerOutcome<A> {
    private final String peerAddress;
    private final A ack;
    private final String failure;

    public static <A> PeerOutcome<A> ok(final String peerAddress, final A ack) {
        return new PeerOutcome<>(peerAddress, ack, null);
    }

    public static <A> PeerOutcome<A> failed(final String peerAddress, final String failure) {
        return new PeerOutcome<>(peerAddress, null, failure == null ? "unknown" : failure);
    }

    public boolean isOk() {
        return ack != null;
    }
}
