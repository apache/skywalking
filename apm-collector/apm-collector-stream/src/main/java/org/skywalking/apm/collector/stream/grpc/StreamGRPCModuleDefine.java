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

package org.skywalking.apm.collector.stream.grpc;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.cluster.ClusterDataListener;
import org.skywalking.apm.collector.core.framework.Handler;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.skywalking.apm.collector.core.server.Server;
import org.skywalking.apm.collector.server.grpc.GRPCServer;
import org.skywalking.apm.collector.stream.StreamModuleDefine;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
import org.skywalking.apm.collector.stream.grpc.handler.RemoteCommonServiceHandler;

/**
 * @author peng-yongsheng
 */
public class StreamGRPCModuleDefine extends StreamModuleDefine {

    public static final String MODULE_NAME = "grpc";

    @Override public String name() {
        return MODULE_NAME;
    }

    @Override protected String group() {
        return StreamModuleGroupDefine.GROUP_NAME;
    }

    @Override protected ModuleConfigParser configParser() {
        return new StreamGRPCConfigParser();
    }

    @Override protected Client createClient() {
        return null;
    }

    @Override protected Server server() {
        return new GRPCServer(StreamGRPCConfig.HOST, StreamGRPCConfig.PORT);
    }

    @Override protected ModuleRegistration registration() {
        return new StreamGRPCModuleRegistration();
    }

    @Override public ClusterDataListener listener() {
        return new StreamGRPCDataListener();
    }

    @Override public List<Handler> handlerList() {
        List<Handler> handlers = new ArrayList<>();
        handlers.add(new RemoteCommonServiceHandler());
        return handlers;
    }
}
