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
        registerHandlers(adminRestRegister());
        // /status/config/ttl stays on the public port too — kept from 10.x
        // for baseline-predictor, which fetches it before any /graphql call.
        publicRestRegister().addHandler(
            new TTLConfigQueryHandler(getManager()),
            Collections.singletonList(HttpMethod.GET)
        );
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            CoreModule.NAME,
            AdminServerModule.NAME,
        };
    }

    /**
     * Register all status / debug HTTP handlers on the admin-server REST host.
     * Status, cluster, alarm-debug, TTL config, and per-query debug-trace
     * routes are admin-host endpoints — they share the admin port with
     * {@code /inspect/*}, {@code /dsl-debugging/*}, and {@code /runtime/rule/*}.
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
     * Resolve the admin-server REST {@link HTTPHandlerRegister}.
     * {@link AdminServerModule} is declared in {@link #requiredModules()}, so
     * the framework guarantees admin-server's {@code prepare()} (which
     * registers this service) has run before any provider's {@code start()}.
     */
    HTTPHandlerRegister adminRestRegister() {
        return getManager().find(AdminServerModule.NAME)
                           .provider()
                           .getService(HTTPHandlerRegister.class);
    }

    /** Public REST register (agent / GraphQL port, 12800 by default). */
    HTTPHandlerRegister publicRestRegister() {
        return getManager().find(CoreModule.NAME)
                           .provider()
                           .getService(HTTPHandlerRegister.class);
    }
}
