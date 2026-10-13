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

import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;

/**
 * The receiving end of the pairing channel: a {@link PairingFact} another node, or this one, routed here lands in
 * this node's {@link SpanPairing}. Registered under {@link #NAME} with the core's worker registry, which is how
 * the remote handler finds it.
 */
public class PairingWorker extends AbstractWorker<PairingFact> {
    public static final String NAME = "TAL_SPAN_PAIRING";

    private final SpanPairing spanPairing;

    public PairingWorker(final ModuleDefineHolder moduleDefineHolder, final SpanPairing spanPairing) {
        super(moduleDefineHolder);
        this.spanPairing = spanPairing;
    }

    @Override
    public void in(final PairingFact fact) {
        spanPairing.receive(fact);
    }
}
