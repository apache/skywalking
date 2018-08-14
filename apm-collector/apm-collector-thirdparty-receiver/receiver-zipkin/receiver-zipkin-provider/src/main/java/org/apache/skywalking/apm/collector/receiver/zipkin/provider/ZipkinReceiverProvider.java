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

package org.apache.skywalking.apm.collector.receiver.zipkin.provider;

import org.apache.skywalking.apm.collector.analysis.metric.define.AnalysisMetricModule;
import org.apache.skywalking.apm.collector.analysis.metric.define.service.IInstanceHeartBeatService;
import org.apache.skywalking.apm.collector.analysis.register.define.AnalysisRegisterModule;
import org.apache.skywalking.apm.collector.analysis.register.define.service.IApplicationIDService;
import org.apache.skywalking.apm.collector.analysis.register.define.service.IInstanceIDService;
import org.apache.skywalking.apm.collector.analysis.register.define.service.INetworkAddressIDService;
import org.apache.skywalking.apm.collector.analysis.register.define.service.IServiceNameService;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.AnalysisSegmentParserModule;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.service.ISegmentParseService;
import org.apache.skywalking.apm.collector.core.module.*;
import org.apache.skywalking.apm.collector.jetty.manager.JettyManagerModule;
import org.apache.skywalking.apm.collector.jetty.manager.service.JettyManagerService;
import org.apache.skywalking.apm.collector.receiver.zipkin.define.ZipkinReceiverModule;
import org.apache.skywalking.apm.collector.receiver.zipkin.provider.handler.SpanV1JettyHandler;
import org.apache.skywalking.apm.collector.receiver.zipkin.provider.handler.SpanV2JettyHandler;
import org.apache.skywalking.apm.collector.receiver.zipkin.provider.transform.Zipkin2SkyWalkingTransfer;
import org.apache.skywalking.apm.collector.server.jetty.JettyServer;

/**
 * @author wusheng
 */
public class ZipkinReceiverProvider extends ModuleProvider {
    public static final String NAME = "default";
    private ZipkinReceiverConfig config;

    public ZipkinReceiverProvider() {
        config = new ZipkinReceiverConfig();
    }

    @Override public String name() {
        return NAME;
    }

    @Override public Class<? extends ModuleDefine> module() {
        return ZipkinReceiverModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override public void prepare() throws ServiceNotProvidedException {

    }

    @Override public void start() throws ServiceNotProvidedException, ModuleStartException {
        ModuleDefine moduleDefine = getManager().find(AnalysisRegisterModule.NAME);
        RegisterServices registerServices = new RegisterServices(moduleDefine.getService(IApplicationIDService.class),
            moduleDefine.getService(IInstanceIDService.class),
            moduleDefine.getService(INetworkAddressIDService.class),
            moduleDefine.getService(IServiceNameService.class));
        IInstanceHeartBeatService instanceHeartBeatService = getManager().find(AnalysisMetricModule.NAME).getService(IInstanceHeartBeatService.class);
        Zipkin2SkyWalkingTransfer.INSTANCE.setRegisterServices(registerServices);
        Zipkin2SkyWalkingTransfer.INSTANCE.setInstanceHeartBeatService(instanceHeartBeatService);

        JettyManagerService managerService = getManager().find(JettyManagerModule.NAME).getService(JettyManagerService.class);
        JettyServer jettyServer = managerService.createIfAbsent(config.getHost(), config.getPort(), config.getContextPath());
        jettyServer.addHandler(new SpanV1JettyHandler(config, registerServices));
        jettyServer.addHandler(new SpanV2JettyHandler(config, registerServices));

        ISegmentParseService segmentParseService = getManager().find(AnalysisSegmentParserModule.NAME).getService(ISegmentParseService.class);
        Receiver2AnalysisBridge bridge = new Receiver2AnalysisBridge(segmentParseService);
        Zipkin2SkyWalkingTransfer.INSTANCE.addListener(bridge);
    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override public String[] requiredModules() {
        return new String[] {JettyManagerModule.NAME, AnalysisSegmentParserModule.NAME, AnalysisMetricModule.NAME};
    }
}
