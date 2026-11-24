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

package org.apache.skywalking.oap.server.admin.trace.pairing;

import com.linecorp.armeria.common.HttpMethod;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.admin.server.cluster.AdminClusterChannelManager;
import org.apache.skywalking.oap.server.admin.server.module.AdminServerModule;
import org.apache.skywalking.oap.server.admin.trace.pairing.cluster.PairingClusterClient;
import org.apache.skywalking.oap.server.admin.trace.pairing.cluster.PairingClusterServiceImpl;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.telemetry.api.TelemetryRelatedContext;
import org.apache.skywalking.oap.trace.analyzer.v2.module.TraceAnalyzerModule;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.SpanPairing;

public class TracePairingDebuggingModuleProvider extends ModuleProvider {
    /**
     * How often an unread capture is checked for having idled out.
     */
    private static final long EXPIRY_SWEEP_SECONDS = 5L;

    private ScheduledExecutorService expirySweeper;

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return TracePairingDebuggingModule.class;
    }

    @Override
    public ConfigCreator<? extends ModuleConfig> newConfigCreator() {
        return new ConfigCreator<TracePairingDebuggingModuleConfig>() {
            @Override
            public Class<TracePairingDebuggingModuleConfig> type() {
                return TracePairingDebuggingModuleConfig.class;
            }

            @Override
            public void onInitialized(final TracePairingDebuggingModuleConfig initialized) {
            }
        };
    }

    @Override
    public void prepare() throws ServiceNotProvidedException {
    }

    @Override
    public void start() throws ServiceNotProvidedException {
        final SpanPairing pairing = getManager().find(TraceAnalyzerModule.NAME).provider()
                                                .getService(SpanPairing.class);
        final PairingCapture capture = new PairingCapture(pairing.getDebugGate());
        final String selfNodeId = TelemetryRelatedContext.INSTANCE.getId();
        final AdminClusterChannelManager channels = getManager().find(AdminServerModule.NAME).provider()
                                                                .getService(AdminClusterChannelManager.class);
        getManager().find(AdminServerModule.NAME).provider().getService(GRPCHandlerRegister.class)
                    .addHandler(new PairingClusterServiceImpl(capture, selfNodeId));
        getManager().find(AdminServerModule.NAME).provider().getService(HTTPHandlerRegister.class)
                    .addHandler(new TracePairingDebuggingRestHandler(capture, new PairingClusterClient(channels),
                        selfNodeId), Arrays.asList(HttpMethod.POST, HttpMethod.GET));
        expirySweeper = Executors.newSingleThreadScheduledExecutor(r -> {
            final Thread t = new Thread(r, "trace-pairing-debugging-expiry");
            t.setDaemon(true);
            return t;
        });
        expirySweeper.scheduleAtFixedRate(() -> capture.expire(System.currentTimeMillis()),
            EXPIRY_SWEEP_SECONDS, EXPIRY_SWEEP_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void notifyAfterCompleted() {
    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            CoreModule.NAME,
            AdminServerModule.NAME,
            TraceAnalyzerModule.NAME,
        };
    }
}
