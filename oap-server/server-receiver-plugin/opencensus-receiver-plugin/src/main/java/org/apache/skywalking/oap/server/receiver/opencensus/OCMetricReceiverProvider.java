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

package org.apache.skywalking.oap.server.receiver.opencensus;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.meter.analyzer.prometheus.PrometheusMetricConverter;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.MetricsRule;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.Rules;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.server.ServerException;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCServer;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;

public class OCMetricReceiverProvider extends ModuleProvider {
    public static final String NAME = "default";
    private OCMetricReceiverConfig config;
    private GRPCServer grpcServer = null;
    private List<MetricsRule> rules;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return OCMetricReceiverModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        config = new OCMetricReceiverConfig();
        return config;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        if (config.getGRPCPort() <= 0) {
           return;
        }
        rules = Rules.loadRules(config.getRulePath()).stream()
            .flatMap(rule -> rule.getMetricsRules().stream())
            .collect(Collectors.toList());
        grpcServer = new GRPCServer(config.getGRPCHost(), config.getGRPCPort());
        if (config.getMaxMessageSize() > 0) {
            grpcServer.setMaxMessageSize(config.getMaxMessageSize());
        }
        if (config.getMaxConcurrentCallsPerConnection() > 0) {
            grpcServer.setMaxConcurrentCallsPerConnection(config.getMaxConcurrentCallsPerConnection());
        }
        if (config.getGRPCThreadPoolQueueSize() > 0) {
            grpcServer.setThreadPoolQueueSize(config.getGRPCThreadPoolQueueSize());
        }
        if (config.getGRPCThreadPoolSize() > 0) {
            grpcServer.setThreadPoolSize(config.getGRPCThreadPoolSize());
        }
        grpcServer.initialize();
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        if (Objects.nonNull(grpcServer)) {
            final MeterSystem service = getManager().find(CoreModule.NAME).provider().getService(MeterSystem.class);
            grpcServer.addHandler(new OCMetricHandler(new PrometheusMetricConverter(rules, null, service)));
        }
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
        try {
            if (Objects.nonNull(grpcServer)) {
                grpcServer.start();
            }
        } catch (ServerException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }

    @Override
    public String[] requiredModules() {
        return new String[] {SharingServerModule.NAME};
    }
}
