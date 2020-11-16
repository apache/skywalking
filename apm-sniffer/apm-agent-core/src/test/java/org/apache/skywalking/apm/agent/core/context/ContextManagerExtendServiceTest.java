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

package org.apache.skywalking.apm.agent.core.context;

import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.test.tools.AgentServiceRule;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Assert;

public class ContextManagerExtendServiceTest {

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @BeforeClass
    public static void beforeClass() {
        Config.Agent.KEEP_TRACING = true;
    }

    @AfterClass
    public static void afterClass() {
        Config.Agent.KEEP_TRACING = false;
        ServiceManager.INSTANCE.shutdown();
    }

    @Test
    public void testRequestUrlCreateTraceContext() {
        ContextManagerExtendService extendService = ServiceManager.INSTANCE.findService(ContextManagerExtendService.class);

        AbstractTracerContext tracerContext = extendService.createTraceContext("/app/demo/index.html", false);
        String traceId = tracerContext.getReadablePrimaryTraceId();
        Assert.assertEquals("Ignored_Trace", traceId);

        tracerContext = extendService.createTraceContext("/app/demo/index.htm", false);
        traceId = tracerContext.getReadablePrimaryTraceId();
        Assert.assertNotEquals("Ignored_Trace", traceId);
    }

}
