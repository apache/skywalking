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

package org.apache.skywalking.apm.agent.core.conf.watcher;

import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.dynamic.AgentConfigChangeWatcher;
import org.apache.skywalking.apm.agent.core.conf.dynamic.watcher.SpanLimitWatcher;
import org.apache.skywalking.apm.agent.core.test.tools.AgentServiceRule;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class SpanLimitWatcherTest {

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    private final SpanLimitWatcher spanLimitWatcher = new SpanLimitWatcher("agent.span_limit_per_segment");

    @Before
    public void setUp() {
    }

    @AfterClass
    public static void afterClass() {
        ServiceManager.INSTANCE.shutdown();
    }

    @Test
    public void testConfigModifyEvent() {
        spanLimitWatcher.notify(new AgentConfigChangeWatcher.ConfigChangeEvent(
            "400",
            AgentConfigChangeWatcher.EventType.MODIFY
        ));
        Assert.assertEquals(400, spanLimitWatcher.getSpanLimit());
        Assert.assertEquals("agent.span_limit_per_segment", spanLimitWatcher.getPropertyKey());
    }

    @Test
    public void testConfigDeleteEvent() {
        spanLimitWatcher.notify(new AgentConfigChangeWatcher.ConfigChangeEvent(
            null,
            AgentConfigChangeWatcher.EventType.DELETE
        ));
        Assert.assertEquals("agent.span_limit_per_segment", spanLimitWatcher.getPropertyKey());
    }
}
