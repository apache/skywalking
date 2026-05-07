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

package org.apache.skywalking.oap.server.admin.dsl.debugging.module;

import org.apache.skywalking.oap.server.admin.dsl.debugging.lal.LALHolderRegistry;
import org.apache.skywalking.oap.server.admin.dsl.debugging.mal.MALHolderRegistry;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;

/**
 * DSL Debug API module. Mounts the DSL debug session control plane and the
 * read-only OAL listing endpoints (used by the debugger UI / CLI rule picker)
 * onto the shared {@code admin-server} HTTP host. Disabled by default; an
 * operator opts in with {@code SW_DSL_DEBUGGING=default}, which in turn
 * requires {@code SW_ADMIN_SERVER=default}.
 *
 * <p>The full design is captured in
 * {@code docs/en/swip/SWIP-13.md}. This module ships in stages:
 * <ul>
 *   <li>Phase 0c (this module's introduction): module skeleton + read-only
 *       OAL file listing + status / session-stub endpoints. Session control
 *       plane returns {@code 501 Not Implemented} for the capture verbs until
 *       per-DSL probes land.</li>
 *   <li>Phases 1–3: per-DSL capture (MAL → LAL → OAL).</li>
 *   <li>Phase 4: cluster fan-out.</li>
 * </ul>
 *
 * <p>SECURITY: routes mount on the admin port, which has no built-in
 * authentication. The captured payloads (raw log bodies, parsed maps, output
 * fields, MAL builder state) are operationally sensitive — operators MUST
 * gateway-protect the admin port with an IP allow-list and an authenticating
 * reverse proxy, bind to a private interface, and never expose it to the
 * public internet. See {@code docs/en/setup/backend/admin-api/readme.md}.
 */
public class DSLDebuggingModule extends ModuleDefine {
    public static final String NAME = "dsl-debugging";

    public DSLDebuggingModule() {
        super(NAME);
    }

    @Override
    public Class[] services() {
        return new Class[] {
            // Static-rule loaders and the runtime-rule apply path register their
            // live GateHolder bindings here so the session registry can resolve a
            // per-rule holder for an install request. One registry per DSL — same
            // shape across MAL / LAL / OAL.
            MALHolderRegistry.class,
            LALHolderRegistry.class,
        };
    }
}
