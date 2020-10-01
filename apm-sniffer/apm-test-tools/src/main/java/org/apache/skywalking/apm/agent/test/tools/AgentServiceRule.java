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

package org.apache.skywalking.apm.agent.test.tools;

import java.util.ArrayList;
import java.util.HashMap;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.IgnoredTracerContext;
import org.apache.skywalking.apm.agent.core.context.TracingContext;
import org.apache.skywalking.apm.agent.core.context.TracingContextListener;
import org.apache.skywalking.apm.agent.core.logging.core.LogLevel;
import org.apache.skywalking.apm.agent.core.plugin.loader.AgentClassLoader;
import org.apache.skywalking.apm.agent.test.helper.FieldSetter;
import org.junit.rules.ExternalResource;

public class AgentServiceRule extends ExternalResource {

    @Override
    protected void after() {
        super.after();
        try {
            FieldSetter.setValue(
                ServiceManager.INSTANCE.getDeclaringClass(), "bootedServices", new HashMap<Class, BootService>());
            FieldSetter.setValue(
                IgnoredTracerContext.ListenerManager.class, "LISTENERS", new ArrayList<TracingContextListener>());
            FieldSetter.setValue(
                TracingContext.ListenerManager.class, "LISTENERS", new ArrayList<TracingContextListener>());
            ServiceManager.INSTANCE.shutdown();
        } catch (Exception e) {
        }
    }

    @Override
    protected void before() throws Throwable {
        super.before();
        AgentClassLoader.initDefaultLoader();
        Config.Logging.LEVEL = LogLevel.OFF;
        ServiceManager.INSTANCE.boot();
        Config.Agent.KEEP_TRACING = true;
    }
}
