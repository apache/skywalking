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


package org.apache.skywalking.apm.agent.core.test.tools;

import java.util.HashMap;
import java.util.LinkedList;

import org.apache.skywalking.apm.agent.core.context.TracingThreadListener;
import org.junit.rules.ExternalResource;
import org.powermock.reflect.Whitebox;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.context.IgnoredTracerContext;
import org.apache.skywalking.apm.agent.core.context.TracingContext;
import org.apache.skywalking.apm.agent.core.context.TracingContextListener;

public class AgentServiceRule extends ExternalResource {

    @Override
    protected void after() {
        super.after();
        Whitebox.setInternalState(ServiceManager.INSTANCE, "bootedServices", new HashMap<Class, BootService>());
        Whitebox.setInternalState(TracingContext.ListenerManager.class, "LISTENERS", new LinkedList<TracingContextListener>());
        Whitebox.setInternalState(IgnoredTracerContext.ListenerManager.class, "LISTENERS", new LinkedList<TracingContextListener>());
        Whitebox.setInternalState(TracingContext.TracingThreadListenerManager.class, "LISTENERS", new LinkedList<TracingThreadListener>());
    }

    @Override
    protected void before() throws Throwable {
        super.before();
        ServiceManager.INSTANCE.boot();
    }
}
