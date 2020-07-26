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

package org.apache.skywalking.oap.server.receiver.zipkin;

import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.ISegmentParserService;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.server.ServerException;
import org.apache.skywalking.oap.server.library.server.jetty.JettyServer;
import org.apache.skywalking.oap.server.library.server.jetty.JettyServerConfig;
import org.apache.skywalking.oap.server.receiver.trace.module.TraceModule;
import org.apache.skywalking.oap.server.receiver.zipkin.analysis.Receiver2AnalysisBridge;
import org.apache.skywalking.oap.server.receiver.zipkin.analysis.transform.Zipkin2SkyWalkingTransfer;
import org.apache.skywalking.oap.server.receiver.zipkin.handler.SpanV1JettyHandler;
import org.apache.skywalking.oap.server.receiver.zipkin.handler.SpanV2JettyHandler;

public class ZipkinReceiverProvider extends ModuleProvider {
    public static final String NAME = "default";
    private ZipkinReceiverConfig config;
    private JettyServer jettyServer;

    public ZipkinReceiverProvider() {
        config = new ZipkinReceiverConfig();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return ZipkinReceiverModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException {

    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        JettyServerConfig jettyServerConfig = JettyServerConfig.builder()
                                                               .host(config.getHost())
                                                               .port(config.getPort())
                                                               .contextPath(config.getContextPath())
                                                               .jettyIdleTimeOut(config.getJettyIdleTimeOut())
                                                               .jettyAcceptorPriorityDelta(
                                                                   config.getJettyAcceptorPriorityDelta())
                                                               .jettyMinThreads(config.getJettyMinThreads())
                                                               .jettyMaxThreads(config.getJettyMaxThreads())
                                                               .jettyAcceptQueueSize(config.getJettyAcceptQueueSize())
                                                               .build();

        jettyServer = new JettyServer(jettyServerConfig);
        jettyServer.initialize();

        jettyServer.addHandler(new SpanV1JettyHandler(config, getManager()));
        jettyServer.addHandler(new SpanV2JettyHandler(config, getManager()));

        if (config.isNeedAnalysis()) {
            ISegmentParserService segmentParseService = getManager().find(TraceModule.NAME)
                                                                    .provider()
                                                                    .getService(ISegmentParserService.class);
            Receiver2AnalysisBridge bridge = new Receiver2AnalysisBridge(segmentParseService);
            Zipkin2SkyWalkingTransfer.INSTANCE.addListener(bridge);
        }
    }

    @Override
    public void notifyAfterCompleted() throws ModuleStartException {
        try {
            jettyServer.start();
        } catch (ServerException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }

    @Override
    public String[] requiredModules() {
        if (config.isNeedAnalysis()) {
            return new String[] {TraceModule.NAME};
        } else {
            /**
             * In pure trace status, we don't need the trace receiver.
             */
            return new String[] {CoreModule.NAME};
        }
    }
}
