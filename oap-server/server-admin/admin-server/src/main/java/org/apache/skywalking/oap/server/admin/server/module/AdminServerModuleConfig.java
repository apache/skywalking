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

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Getter
@Setter
public class AdminServerModuleConfig extends ModuleConfig {
    /**
     * Bind address for the admin HTTP host. Default {@code 0.0.0.0}.
     * <p>SECURITY: set this to a private interface (or {@code 127.0.0.1}) — the
     * admin port has no built-in authentication, so operators MUST gateway-protect
     * the endpoints with an IP allow-list and an authenticating reverse proxy, and
     * MUST NOT expose this address to the public internet.
     */
    private String host = "0.0.0.0";
    /** Default {@code 17128}. Admin HTTP host port shared across feature modules. */
    private int port = 17128;
    private String contextPath = "/";
    private int idleTimeOut = 30_000;
    private int acceptQueueSize = 0;
    private int httpMaxRequestHeaderSize = 8192;

    /**
     * Bind address for the admin-internal gRPC host that carries peer-to-peer
     * cluster RPCs for admin features (dsl-debugging install/collect/stop,
     * runtime-rule Suspend/Resume/Forward). Default {@code 0.0.0.0}.
     * <p>SECURITY: this port MUST be reachable peer-to-peer between OAP nodes
     * and MUST NEVER be exposed to the agent network or operators. It is
     * intentionally separate from the public agent / cluster gRPC port
     * ({@code core.gRPCPort}, default 11800) so an attacker on the agent
     * network cannot invoke privileged admin RPCs.
     */
    private String gRPCHost = "0.0.0.0";

    /**
     * Default {@code 17129}. Internal gRPC port for peer-to-peer admin
     * cluster RPCs. Bound only when the admin-server module is enabled.
     * Every OAP in the cluster is assumed to use the same value — peers
     * are discovered by host via the cluster module and dialed at this
     * port. If you change it, change it on every node.
     */
    private int gRPCPort = 17129;

    private int gRPCMaxConcurrentCallsPerConnection = 0;
    private int gRPCMaxMessageSize = 50 * 1024 * 1024; // 50 MiB
    private int gRPCThreadPoolSize = 0;
    private int gRPCThreadPoolQueueSize = 0;
    private boolean gRPCSslEnabled = false;
    private String gRPCSslKeyPath = "";
    private String gRPCSslCertChainPath = "";
    private String gRPCSslTrustedCAsPath = "";

    /**
     * Default per-call timeout (ms) for admin-internal RPCs between OAP nodes —
     * dsl-debugging {@code install / collect / stop} fan-outs and any other
     * admin-internal RPC that doesn't override its own deadline. Workflow-specific
     * call sites (runtime-rule {@code Suspend}, {@code Forward}) keep their own
     * tuned values because their latency budgets are determined by the workflow,
     * not by a generic timeout.
     */
    private int internalCommunicationTimeout = 5_000;
}
