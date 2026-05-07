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

package org.apache.skywalking.oap.server.admin.server.module;

import org.apache.skywalking.oap.server.admin.server.cluster.AdminClusterChannelManager;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;

/**
 * Shared HTTP host for admin / on-demand write APIs (runtime-rule hot-update,
 * DSL debug session, OAL read-only listing). Disabled by default; an operator
 * opts in by setting {@code SW_ADMIN_SERVER=default}. Feature modules
 * (runtime-rule, dsl-debugging) declare {@code AdminServerModule.NAME} in their
 * {@code requiredModules()} and register their REST handlers via the
 * {@link HTTPHandlerRegister} service this module exposes.
 *
 * <p>The admin port has NO built-in authentication. Operators MUST gateway-protect
 * it with an IP allow-list and an authenticating reverse proxy, bind to a private
 * interface, and never expose it to the public internet. See
 * {@code docs/en/setup/backend/admin-api/readme.md} for the full security
 * notice.
 */
public class AdminServerModule extends ModuleDefine {
    public static final String NAME = "admin-server";

    public AdminServerModule() {
        super(NAME);
    }

    @Override
    public Class[] services() {
        return new Class[] {
            HTTPHandlerRegister.class,
            // Admin-internal gRPC infrastructure (default port 17129) — separate
            // from the public agent / cluster gRPC port (default 11800) so
            // privileged admin RPCs (dsl-debugging install/collect, runtime-rule
            // Suspend/Resume/Forward) never share a blast radius with agent
            // telemetry.
            GRPCHandlerRegister.class,
            AdminClusterChannelManager.class,
        };
    }
}
