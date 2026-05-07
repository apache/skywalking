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

import io.netty.handler.ssl.SslContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.apache.skywalking.oap.server.admin.server.cluster.AdminClusterChannelManager;
import org.apache.skywalking.oap.server.admin.server.cluster.AdminClusterChannelManagerImpl;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.RunningMode;
import org.apache.skywalking.oap.server.core.cluster.ClusterModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterNodesQuery;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegisterImpl;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegisterImpl;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.server.ServerException;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCServer;
import org.apache.skywalking.oap.server.library.server.http.HTTPServer;
import org.apache.skywalking.oap.server.library.server.http.HTTPServerConfig;

/**
 * Owns the Armeria HTTP host shared by every admin / on-demand write feature.
 * The provider is loaded only when an operator enables the module via the
 * {@code SW_ADMIN_SERVER=default} selector. Until then the admin port never
 * opens — feature modules that depend on {@link AdminServerModule#NAME} fail
 * fast at boot with a {@code ModuleNotFoundException}.
 *
 * <p>The provider exposes {@link HTTPHandlerRegister} as a service. Feature
 * modules ({@code receiver-runtime-rule}, {@code dsl-debugging}) call
 * {@code moduleManager.find(AdminServerModule.NAME).provider().getService(HTTPHandlerRegister.class)}
 * during their {@code start()} phase and add their own handlers. The HTTP
 * server itself is initialized in {@link #prepare()} and started in
 * {@link #notifyAfterCompleted()} — the same lifecycle the other Armeria-hosting
 * providers in OAP follow, so handlers added during {@code start()} are picked
 * up when the server actually accepts connections.
 *
 * <p>SECURITY: the admin port has no built-in authentication. Operators MUST
 * gateway-protect the endpoints, bind to a private interface, and never expose
 * the port to the public internet. See
 * {@code docs/en/setup/backend/admin-api/readme.md}.
 */
@Slf4j
public class AdminServerModuleProvider extends ModuleProvider {

    private AdminServerModuleConfig moduleConfig;
    private HTTPServer httpServer;
    private GRPCServer grpcServer;
    private AdminClusterChannelManagerImpl peerChannelManager;

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return AdminServerModule.class;
    }

    @Override
    public ConfigCreator newConfigCreator() {
        return new ConfigCreator<AdminServerModuleConfig>() {
            @Override
            public Class type() {
                return AdminServerModuleConfig.class;
            }

            @Override
            public void onInitialized(final AdminServerModuleConfig initialized) {
                moduleConfig = initialized;
            }
        };
    }

    @Override
    public void prepare() {
        if (moduleConfig.getPort() <= 0) {
            throw new IllegalStateException(
                "admin-server: port must be > 0 when the module is enabled, got "
                    + moduleConfig.getPort());
        }
        final HTTPServerConfig httpServerConfig =
            HTTPServerConfig.builder()
                            .host(Strings.isBlank(moduleConfig.getHost()) ? "0.0.0.0"
                                      : moduleConfig.getHost())
                            .port(moduleConfig.getPort())
                            .contextPath(moduleConfig.getContextPath())
                            .acceptQueueSize(moduleConfig.getAcceptQueueSize())
                            .idleTimeOut(moduleConfig.getIdleTimeOut())
                            .maxRequestHeaderSize(moduleConfig.getHttpMaxRequestHeaderSize())
                            .build();
        httpServer = new HTTPServer(httpServerConfig);
        httpServer.setBlockingTaskName("admin-http");
        httpServer.initialize();
        registerServiceImplementation(HTTPHandlerRegister.class,
                                      new HTTPHandlerRegisterImpl(httpServer));

        // Admin-internal gRPC server — peer-to-peer cluster RPCs for admin
        // features. Bound separately from the public agent / cluster gRPC
        // port (default 11800) so privileged admin RPCs (dsl-debugging
        // install/collect, runtime-rule Suspend/Resume/Forward) never share
        // a blast radius with agent telemetry. Operators bind this to a
        // private peer-to-peer interface; the cluster module dials each
        // peer at this port via AdminClusterChannelManager.
        if (moduleConfig.getGRPCPort() <= 0) {
            throw new IllegalStateException(
                "admin-server: gRPCPort must be > 0 when the module is enabled, got "
                    + moduleConfig.getGRPCPort());
        }
        if (moduleConfig.isGRPCSslEnabled()) {
            grpcServer = new GRPCServer(
                Strings.isBlank(moduleConfig.getGRPCHost()) ? "0.0.0.0" : moduleConfig.getGRPCHost(),
                moduleConfig.getGRPCPort(),
                moduleConfig.getGRPCSslCertChainPath(),
                moduleConfig.getGRPCSslKeyPath(),
                moduleConfig.getGRPCSslTrustedCAsPath());
        } else {
            grpcServer = new GRPCServer(
                Strings.isBlank(moduleConfig.getGRPCHost()) ? "0.0.0.0" : moduleConfig.getGRPCHost(),
                moduleConfig.getGRPCPort());
        }
        if (moduleConfig.getGRPCMaxConcurrentCallsPerConnection() > 0) {
            grpcServer.setMaxConcurrentCallsPerConnection(
                moduleConfig.getGRPCMaxConcurrentCallsPerConnection());
        }
        if (moduleConfig.getGRPCMaxMessageSize() > 0) {
            grpcServer.setMaxMessageSize(moduleConfig.getGRPCMaxMessageSize());
        }
        if (moduleConfig.getGRPCThreadPoolSize() > 0) {
            grpcServer.setThreadPoolSize(moduleConfig.getGRPCThreadPoolSize());
        }
        grpcServer.setThreadPoolName("admin-grpc");
        grpcServer.initialize();
        registerServiceImplementation(GRPCHandlerRegister.class,
                                      new GRPCHandlerRegisterImpl(grpcServer));

        // Peer channel manager: register IMMEDIATELY in prepare() so the
        // framework's requiredCheck (which fires before any provider's
        // start()) sees this service. ClusterNodesQuery isn't available
        // here yet — pass a supplier that resolves it lazily on first
        // reconcile, which fires from notifyAfterCompleted() after every
        // module's start() has run.
        // When the server side is TLS-enabled, the client side MUST also use TLS — a
        // missing or empty trusted-CAs path on the client would leave us dialling peers
        // in plaintext at a port the server is only willing to handshake. Fail fast at
        // boot rather than letting the cluster silently break at first reconcile.
        SslContext clientSslContext = null;
        if (moduleConfig.isGRPCSslEnabled()) {
            if (moduleConfig.getGRPCSslTrustedCAsPath() == null
                || moduleConfig.getGRPCSslTrustedCAsPath().isEmpty()) {
                throw new IllegalStateException(
                    "admin-server: gRPCSslEnabled=true but gRPCSslTrustedCAsPath is empty. "
                        + "The admin-internal gRPC bus needs a CA bundle on every node so "
                        + "peer channels can establish TLS to the server's cert. Set "
                        + "SW_ADMIN_SERVER_GRPC_SSL_TRUSTED_CAS_PATH (or "
                        + "admin-server.gRPCSslTrustedCAsPath in application.yml) on every "
                        + "OAP, or set gRPCSslEnabled=false everywhere.");
            }
            try {
                clientSslContext = AdminClusterChannelManagerImpl.clientSslContext(
                    moduleConfig.getGRPCSslTrustedCAsPath());
            } catch (final Exception e) {
                throw new IllegalStateException(
                    "admin-server: failed to build admin gRPC client SSL context", e);
            }
        }
        peerChannelManager = new AdminClusterChannelManagerImpl(
            () -> getManager().find(ClusterModule.NAME).provider()
                              .getService(ClusterNodesQuery.class),
            moduleConfig.getGRPCPort(),
            moduleConfig.getInternalCommunicationTimeout(),
            clientSslContext);
        registerServiceImplementation(AdminClusterChannelManager.class, peerChannelManager);
    }

    @Override
    public void start() {
        // Routes are added by feature modules in their start() phase via the
        // HTTPHandlerRegister service exposed above. Channel manager is
        // already registered in prepare() with a lazy ClusterNodesQuery
        // supplier; nothing else to do here.
    }

    @Override
    public void notifyAfterCompleted() throws ModuleStartException {
        if (RunningMode.isInitMode()) {
            return;
        }
        try {
            if (grpcServer != null) {
                grpcServer.start();
                log.info("admin-server gRPC listening on {}:{} (peer-to-peer admin RPCs only — "
                             + "MUST be reachable between OAP nodes; MUST NOT be exposed to the agent network "
                             + "or operators).",
                         moduleConfig.getGRPCHost(), moduleConfig.getGRPCPort());
            }
        } catch (final ServerException e) {
            throw new ModuleStartException("admin-server: failed to start gRPC server", e);
        }
        if (peerChannelManager != null) {
            peerChannelManager.start();
        }
        if (httpServer != null) {
            httpServer.start();
            log.info(
                "admin-server HTTP listening on {}:{} (no built-in authentication — "
                    + "gateway-protect with an IP allow-list and authenticating reverse proxy; "
                    + "never expose to the public internet).",
                moduleConfig.getHost(), moduleConfig.getPort()
            );
        }
    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            CoreModule.NAME,    // GRPCServer + GRPCHandlerRegisterImpl live here
            ClusterModule.NAME, // ClusterNodesQuery for peer discovery
        };
    }
}
