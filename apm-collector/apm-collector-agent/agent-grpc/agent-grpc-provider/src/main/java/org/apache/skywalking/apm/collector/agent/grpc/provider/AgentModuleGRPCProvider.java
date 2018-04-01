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

package org.apache.skywalking.apm.collector.agent.grpc.provider;

import org.apache.skywalking.apm.collector.agent.grpc.define.AgentGRPCModule;
import org.apache.skywalking.apm.collector.agent.grpc.provider.handler.*;
import org.apache.skywalking.apm.collector.agent.grpc.provider.handler.naming.AgentGRPCNamingHandler;
import org.apache.skywalking.apm.collector.agent.grpc.provider.handler.naming.AgentGRPCNamingListener;
import org.apache.skywalking.apm.collector.analysis.metric.define.AnalysisMetricModule;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.AnalysisSegmentParserModule;
import org.apache.skywalking.apm.collector.cluster.ClusterModule;
import org.apache.skywalking.apm.collector.cluster.service.ModuleListenerService;
import org.apache.skywalking.apm.collector.cluster.service.ModuleRegisterService;
import org.apache.skywalking.apm.collector.core.module.Module;
import org.apache.skywalking.apm.collector.core.module.ModuleProvider;
import org.apache.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.apache.skywalking.apm.collector.grpc.manager.GRPCManagerModule;
import org.apache.skywalking.apm.collector.grpc.manager.service.GRPCManagerService;
import org.apache.skywalking.apm.collector.naming.NamingModule;
import org.apache.skywalking.apm.collector.naming.service.NamingHandlerRegisterService;
import org.apache.skywalking.apm.collector.server.grpc.GRPCServer;
import org.eclipse.jetty.util.StringUtil;

import java.io.File;
import java.util.Properties;

/**
 * @author peng-yongsheng
 */
public class AgentModuleGRPCProvider extends ModuleProvider {

    public static final String NAME = "gRPC";
    private static final String HOST = "host";
    private static final String PORT = "port";
    private static final String SSL_CERT_CHAIN_FILEPATH = "ssl_cert_chain_file";
    private static final String SSL_PRIVATE_KEY_FILE = "ssl_private_key_file";
    private static final String AUTHENTICATION = "authentication";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<? extends Module> module() {
        return AgentGRPCModule.class;
    }

    @Override
    public void prepare(Properties config) throws ServiceNotProvidedException {

    }

    @Override
    public void start(Properties config) throws ServiceNotProvidedException {
        String host = config.getProperty(HOST);
        Integer port = (Integer) config.get(PORT);
        String sslCertChainFilePath = config.getProperty(SSL_CERT_CHAIN_FILEPATH);
        String sslPrivateKeyFilePath = config.getProperty(SSL_PRIVATE_KEY_FILE);

        AuthenticationSimpleChecker.INSTANCE.setExpectedToken(config.getProperty(AUTHENTICATION, ""));
        File sslCertChainFile = null;
        File sslPrivateKeyFile = null;
        if (StringUtil.isNotBlank(sslCertChainFilePath)) {
            sslCertChainFile = new File(sslCertChainFilePath);
            if (!(sslCertChainFile.exists() && sslCertChainFile.isFile())) {
                sslCertChainFile = null;
            }
        }
        if (StringUtil.isNotBlank(sslPrivateKeyFilePath)) {
            sslPrivateKeyFile = new File(sslPrivateKeyFilePath);
            if (!(sslPrivateKeyFile.exists() && sslPrivateKeyFile.isFile())) {
                sslPrivateKeyFile = null;
            }
        }

        ModuleRegisterService moduleRegisterService = getManager().find(ClusterModule.NAME).getService(ModuleRegisterService.class);
        moduleRegisterService.register(AgentGRPCModule.NAME, this.name(), new AgentModuleGRPCRegistration(host, port));

        AgentGRPCNamingListener namingListener = new AgentGRPCNamingListener();
        ModuleListenerService moduleListenerService = getManager().find(ClusterModule.NAME).getService(ModuleListenerService.class);
        moduleListenerService.addListener(namingListener);

        NamingHandlerRegisterService namingHandlerRegisterService = getManager().find(NamingModule.NAME).getService(NamingHandlerRegisterService.class);
        namingHandlerRegisterService.register(new AgentGRPCNamingHandler(namingListener));

        GRPCManagerService managerService = getManager().find(GRPCManagerModule.NAME).getService(GRPCManagerService.class);
        GRPCServer gRPCServer;
        if (sslCertChainFile != null && sslPrivateKeyFile != null) {
            gRPCServer = managerService.createIfAbsent(host, port, sslCertChainFile, sslPrivateKeyFile);
        } else {
            gRPCServer = managerService.createIfAbsent(host, port);
        }

        addHandlers(gRPCServer);
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override
    public String[] requiredModules() {
        return new String[]{ClusterModule.NAME, NamingModule.NAME, GRPCManagerModule.NAME, AnalysisSegmentParserModule.NAME, AnalysisMetricModule.NAME};
    }

    private void addHandlers(GRPCServer gRPCServer) {
        AuthenticationSimpleChecker.INSTANCE.build(gRPCServer, new ApplicationRegisterServiceHandler(getManager()));
        AuthenticationSimpleChecker.INSTANCE.build(gRPCServer, new InstanceDiscoveryServiceHandler(getManager()));
        AuthenticationSimpleChecker.INSTANCE.build(gRPCServer, new ServiceNameDiscoveryServiceHandler(getManager()));
        AuthenticationSimpleChecker.INSTANCE.build(gRPCServer, new JVMMetricsServiceHandler(getManager()));
        AuthenticationSimpleChecker.INSTANCE.build(gRPCServer, new TraceSegmentServiceHandler(getManager()));
        AuthenticationSimpleChecker.INSTANCE.build(gRPCServer, new NetworkAddressRegisterServiceHandler(getManager()));
    }
}
