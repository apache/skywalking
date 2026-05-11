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

package org.apache.skywalking.oap.server.admin.status;

import com.linecorp.armeria.common.HttpMethod;
import java.util.Collections;
import org.apache.skywalking.oap.server.admin.server.module.AdminServerModule;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;

/**
 * Provider for the {@link StatusModule}. Skeleton in this commit — handler
 * relocation and dual-bind logic land in the next commits.
 */
public class StatusModuleProvider extends ModuleProvider {
    public static final String NAME = "default";

    private StatusModuleConfig config;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return StatusModule.class;
    }

    @Override
    public ConfigCreator<? extends ModuleConfig> newConfigCreator() {
        return new ConfigCreator<StatusModuleConfig>() {
            @Override
            public Class<StatusModuleConfig> type() {
                return StatusModuleConfig.class;
            }

            @Override
            public void onInitialized(final StatusModuleConfig initialized) {
                config = initialized;
            }
        };
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        registerServiceImplementation(AlarmStatusQueryService.class, new AlarmStatusQueryService(getManager()));
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        registerHandlers(publicRestRegister());
        final HTTPHandlerRegister adminRegister = adminRestRegisterOrNull();
        if (adminRegister != null) {
            registerHandlers(adminRegister);
        }
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            CoreModule.NAME,
        };
    }

    /**
     * Register all status / debug HTTP handlers on the supplied
     * {@link HTTPHandlerRegister}. Same set the legacy
     * {@code status-query-plugin} hosted, in the same order. Called for each
     * surface the module dual-binds onto:
     * <ul>
     *   <li>the public REST register exposed by {@link CoreModule} — always,
     *       so skywalking-ui (which calls {@code /status/cluster/nodes},
     *       {@code /status/config/ttl}, {@code /debugging/config/dump} on
     *       {@code core.restPort}) keeps working;</li>
     *   <li>the admin-server register, when admin-server is enabled, so
     *       operators driving admin-host see the same surface alongside
     *       {@code /inspect/*}, {@code /dsl-debugging/*}, and
     *       {@code /runtime/rule/*}.</li>
     * </ul>
     */
    void registerHandlers(final HTTPHandlerRegister register) {
        final ModuleManager manager = getManager();
        register.addHandler(
            new DebuggingHTTPHandler(manager, config),
            Collections.singletonList(HttpMethod.GET)
        );
        register.addHandler(
            new TTLConfigQueryHandler(manager),
            Collections.singletonList(HttpMethod.GET)
        );
        register.addHandler(
            new ClusterStatusQueryHandler(manager),
            Collections.singletonList(HttpMethod.GET)
        );
        register.addHandler(
            new AlarmStatusQueryHandler(manager),
            Collections.singletonList(HttpMethod.GET)
        );
    }

    /**
     * Resolve the public REST {@link HTTPHandlerRegister} via {@link CoreModule}.
     * This binding is unconditional — the public surface preserves the URI
     * binding {@code skywalking-ui} consumes today.
     */
    HTTPHandlerRegister publicRestRegister() {
        return getManager().find(CoreModule.NAME)
                           .provider()
                           .getService(HTTPHandlerRegister.class);
    }

    /**
     * Resolve the admin-server {@link HTTPHandlerRegister} when admin-server
     * is loaded; return {@code null} otherwise. {@link AdminServerModule} is
     * deliberately NOT in {@link #requiredModules()} so that admin-server
     * may stay off without breaking the public-REST status binding.
     * Admin-server registers its {@link HTTPHandlerRegister} service in
     * {@code prepare()} (admin-server/.../AdminServerModuleProvider.java:116),
     * which the framework guarantees has run before any provider's
     * {@code start()}, so this lookup is safe at start-time.
     */
    HTTPHandlerRegister adminRestRegisterOrNull() {
        final ModuleManager manager = getManager();
        if (!manager.has(AdminServerModule.NAME)) {
            return null;
        }
        return manager.find(AdminServerModule.NAME)
                      .provider()
                      .getService(HTTPHandlerRegister.class);
    }
}
