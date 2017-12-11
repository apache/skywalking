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


package org.apache.skywalking.apm.collector.agent.stream;

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.apm.collector.agent.stream.service.jvm.IGCMetricService;
import org.apache.skywalking.apm.collector.agent.stream.service.jvm.IInstanceHeartBeatService;
import org.apache.skywalking.apm.collector.agent.stream.service.jvm.IMemoryMetricService;
import org.apache.skywalking.apm.collector.agent.stream.service.jvm.IMemoryPoolMetricService;
import org.apache.skywalking.apm.collector.agent.stream.service.register.IInstanceIDService;
import org.apache.skywalking.apm.collector.agent.stream.service.jvm.ICpuMetricService;
import org.apache.skywalking.apm.collector.agent.stream.service.register.IApplicationIDService;
import org.apache.skywalking.apm.collector.agent.stream.service.register.IServiceNameService;
import org.apache.skywalking.apm.collector.agent.stream.service.trace.ITraceSegmentService;
import org.apache.skywalking.apm.collector.core.module.Module;

/**
 * @author peng-yongsheng
 */
public class AgentStreamModule extends Module {

    public static final String NAME = "agent_stream";

    @Override public String name() {
        return NAME;
    }

    @Override public Class[] services() {
        List<Class> classes = new ArrayList<>();

        addRegisterService(classes);
        addJVMService(classes);
        classes.add(ITraceSegmentService.class);

        return classes.toArray(new Class[] {});
    }

    private void addRegisterService(List<Class> classes) {
        classes.add(IApplicationIDService.class);
        classes.add(IInstanceIDService.class);
        classes.add(IServiceNameService.class);
    }

    private void addJVMService(List<Class> classes) {
        classes.add(ICpuMetricService.class);
        classes.add(IGCMetricService.class);
        classes.add(IMemoryMetricService.class);
        classes.add(IMemoryPoolMetricService.class);
        classes.add(IInstanceHeartBeatService.class);
    }
}
