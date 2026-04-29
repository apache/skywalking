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

package org.apache.skywalking.oap.server.receiver.runtimerule.reconcile;

/**
 * Outcome of a Suspend request through {@link SuspendResumeCoordinator#localSuspend}
 * or {@link SuspendResumeCoordinator#peerSuspend}. Distinct values cover the four
 * cases the cluster RPC handlers and REST handler need to disambiguate so the cluster
 * RPC handlers can map the result to the wire protocol's ack states and the REST
 * handler can distinguish "this node was already suspended by me" from "rejected
 * because the other origin already holds it".
 */
public enum SuspendResult {
    /** Bundle transitioned RUNNING → SUSPENDED; dispatch parked. */
    SUSPENDED,
    /** Bundle was already SUSPENDED with this origin — idempotent replay, no state change. */
    ALREADY_SUSPENDED,
    /** Bundle does not exist on this node. */
    NOT_PRESENT,
    /**
     * Request refused: the OTHER origin already holds this bundle SUSPENDED. Rejecting
     * instead of merging to BOTH because correct routing never produces cross-origin
     * concurrency — reaching this branch signals misrouted request or split-brain.
     * Caller logs WARN and propagates rejection.
     */
    REJECTED_ORIGIN_CONFLICT
}
