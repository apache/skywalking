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
 * What became of a matched pair of halves on the node that owns the trace.
 */
public enum MatchOutcome {
    /**
     * The alias was pushed to the alias stream; whether storage writes it is {@code NetworkAddressAlias#combine}'s
     * decision.
     */
    REGISTERED("registered"),
    /**
     * The caller dialed a peer listed in {@code gateways.yml}, which keeps its own node, so no alias is built.
     */
    GATEWAY("gateway"),
    /**
     * The callee's rule composed no address: it is not loaded on this node, or its expression failed or returned
     * nothing.
     */
    UNCOMPOSED("uncomposed"),
    /**
     * The composed address does not fit the alias column.
     */
    TOO_LONG("tooLong"),
    /**
     * The callee's service name formats to an empty name, so there is no service to alias.
     */
    NO_SERVICE("noService"),
    /**
     * The address was composed with no registrar to push it to, as before the provider wires one.
     */
    COMPOSED("composed");

    @Getter
    private final String code;

    MatchOutcome(final String code) {
        this.code = code;
    }
}
