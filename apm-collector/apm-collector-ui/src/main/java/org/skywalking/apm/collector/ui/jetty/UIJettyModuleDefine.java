/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.ui.jetty;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.collector.core.cluster.ClusterDataListener;
import org.skywalking.apm.collector.core.framework.Handler;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.skywalking.apm.collector.core.server.Server;
import org.skywalking.apm.collector.server.jetty.JettyServer;
import org.skywalking.apm.collector.ui.UIModuleDefine;
import org.skywalking.apm.collector.ui.UIModuleGroupDefine;
import org.skywalking.apm.collector.ui.jetty.handler.SegmentTopGetHandler;
import org.skywalking.apm.collector.ui.jetty.handler.SpanGetHandler;
import org.skywalking.apm.collector.ui.jetty.handler.TraceDagGetHandler;
import org.skywalking.apm.collector.ui.jetty.handler.TraceStackGetHandler;
import org.skywalking.apm.collector.ui.jetty.handler.application.ApplicationsGetHandler;
import org.skywalking.apm.collector.ui.jetty.handler.instancehealth.InstanceHealthGetHandler;
import org.skywalking.apm.collector.ui.jetty.handler.instancemetric.InstanceMetricGetOneTimeBucketHandler;
import org.skywalking.apm.collector.ui.jetty.handler.instancemetric.InstanceMetricGetRangeTimeBucketHandler;
import org.skywalking.apm.collector.ui.jetty.handler.instancemetric.InstanceOsInfoGetHandler;
import org.skywalking.apm.collector.ui.jetty.handler.servicetree.EntryServiceGetHandler;
import org.skywalking.apm.collector.ui.jetty.handler.servicetree.ServiceTreeGetByIdHandler;
import org.skywalking.apm.collector.ui.jetty.handler.time.AllInstanceLastTimeGetHandler;
import org.skywalking.apm.collector.ui.jetty.handler.time.OneInstanceLastTimeGetHandler;

/**
 * @author peng-yongsheng
 */
public class UIJettyModuleDefine extends UIModuleDefine {

    public static final String MODULE_NAME = "jetty";

    @Override protected String group() {
        return UIModuleGroupDefine.GROUP_NAME;
    }

    @Override public String name() {
        return MODULE_NAME;
    }

    @Override protected ModuleConfigParser configParser() {
        return new UIJettyConfigParser();
    }

    @Override protected Server server() {
        return new JettyServer(UIJettyConfig.HOST, UIJettyConfig.PORT, UIJettyConfig.CONTEXT_PATH);
    }

    @Override protected ModuleRegistration registration() {
        return new UIJettyModuleRegistration();
    }

    @Override public ClusterDataListener listener() {
        return new UIJettyDataListener();
    }

    @Override public List<Handler> handlerList() {
        List<Handler> handlers = new LinkedList<>();
        handlers.add(new TraceDagGetHandler());
        handlers.add(new SegmentTopGetHandler());
        handlers.add(new TraceStackGetHandler());
        handlers.add(new SpanGetHandler());
        handlers.add(new OneInstanceLastTimeGetHandler());
        handlers.add(new AllInstanceLastTimeGetHandler());
        handlers.add(new InstanceHealthGetHandler());
        handlers.add(new ApplicationsGetHandler());
        handlers.add(new InstanceOsInfoGetHandler());
        handlers.add(new InstanceMetricGetOneTimeBucketHandler());
        handlers.add(new InstanceMetricGetRangeTimeBucketHandler());
        handlers.add(new EntryServiceGetHandler());
        handlers.add(new ServiceTreeGetByIdHandler());
        return handlers;
    }
}
