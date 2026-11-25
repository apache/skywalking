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

/**
 * Sees the pairing on the node that owns a trace, for a debugging capture. It runs on the thread that received a
 * half, or that evicted a waiting one, so it returns fast; what it throws is logged and dropped.
 */
public interface PairingDebugListener {
    /**
     * A half reached this node.
     *
     * @param half  the half received
     * @param match the call it completed with the half waiting for it; null when it waits in turn
     */
    void received(PairingFact half, PairingMatch match);

    /**
     * A half waiting on this node left the buffer without meeting its pair.
     *
     * @param half  the half that waited
     * @param cause {@code expired} after the buffer's 60 seconds, {@code evicted} by a full buffer, or
     *              {@code replaced} by a later half of the same key
     */
    void leftUnmatched(PairingFact half, String cause);
}
