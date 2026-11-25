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
import lombok.RequiredArgsConstructor;

/**
 * A call whose two halves met on the node that owns its trace, and the alias built from it.
 */
@Getter
@RequiredArgsConstructor
public class PairingMatch {
    private final PairingFact caller;
    private final PairingFact callee;
    private final MatchOutcome outcome;
    /**
     * The address the callee's rule composed; null when it composed none, or the caller dialed a gateway.
     */
    private final String address;
    /**
     * Why the callee's address expression threw; null when it did not.
     */
    private final String failure;
}
