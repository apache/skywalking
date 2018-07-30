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

package org.apache.skywalking.oap.server.core;

import java.util.*;
import org.apache.skywalking.oap.server.core.analysis.indicator.define.IndicatorMapper;
import org.apache.skywalking.oap.server.core.analysis.worker.define.WorkerMapper;
import org.apache.skywalking.oap.server.core.receiver.SourceReceiver;
import org.apache.skywalking.oap.server.core.remote.RemoteSenderService;
import org.apache.skywalking.oap.server.core.remote.client.RemoteClientManager;
import org.apache.skywalking.oap.server.core.server.*;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;

/**
 * @author peng-yongsheng
 */
public class CoreModule extends ModuleDefine {

    public static final String NAME = "core";

    @Override public String name() {
        return NAME;
    }

    @Override public Class[] services() {
        List<Class> classes = new ArrayList<>();
        addServerInterface(classes);
        addReceiverInterface(classes);
        addInsideService(classes);

        return classes.toArray(new Class[] {});
    }

    private void addServerInterface(List<Class> classes) {
        classes.add(GRPCHandlerRegister.class);
        classes.add(JettyHandlerRegister.class);
    }

    private void addInsideService(List<Class> classes) {
        classes.add(IndicatorMapper.class);
        classes.add(WorkerMapper.class);
        classes.add(RemoteClientManager.class);
        classes.add(RemoteSenderService.class);
    }

    private void addReceiverInterface(List<Class> classes) {
        classes.add(SourceReceiver.class);
    }
}
