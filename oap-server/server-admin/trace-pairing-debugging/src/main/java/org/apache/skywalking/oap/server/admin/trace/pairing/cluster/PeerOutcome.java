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

package org.apache.skywalking.oap.server.admin.trace.pairing.cluster;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * One peer's answer to a fanned-out call: its ack, or why there is none.
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class PeerOutcome<A> {
    private final String peer;
    private final A ack;
    private final String error;

    public static <A> PeerOutcome<A> ok(final String peer, final A ack) {
        return new PeerOutcome<>(peer, ack, null);
    }

    public static <A> PeerOutcome<A> failed(final String peer, final String error) {
        return new PeerOutcome<>(peer, null, error);
    }

    public boolean isOk() {
        return ack != null;
    }
}
