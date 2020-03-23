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
import org.apache.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CorrelationContextTest {

    @Before
    public void setupConfig() {
        Config.Correlation.KEY_COUNT = 2;
        Config.Correlation.VALUE_LENGTH = 8;

        RemoteDownstreamConfig.Agent.SERVICE_ID = 1;
        RemoteDownstreamConfig.Agent.SERVICE_INSTANCE_ID = 1;
    }

    @AfterClass
    public static void afterClass() {
        ServiceManager.INSTANCE.shutdown();
    }

    @Test
    public void testSet() {
        final CorrelationContext context = new CorrelationContext();

        // manual set
        CorrelationContext.SettingResult settingResult = context.set("test1", "t1");
        Assert.assertNotNull(settingResult);
        Assert.assertNull(settingResult.errorMessage());
        Assert.assertNull(settingResult.previousData());

        // set with replace old value
        settingResult = context.set("test1", "t1New");
        Assert.assertNotNull(settingResult);
        Assert.assertNull(settingResult.errorMessage());
        Assert.assertEquals("t1", settingResult.previousData());

        // manual set
        settingResult = context.set("test2", "t2");
        Assert.assertNotNull(settingResult);
        Assert.assertNull(settingResult.errorMessage());
        Assert.assertNull(settingResult.previousData());

        // out of key count
        settingResult = context.set("test3", "t3");
        Assert.assertNotNull(settingResult);
        Assert.assertNotNull(settingResult.errorMessage());
        Assert.assertNull(settingResult.previousData());

        // key not null
        settingResult = context.set(null, "t3");
        Assert.assertNotNull(settingResult);
        Assert.assertNotNull(settingResult.errorMessage());
        Assert.assertNull(settingResult.previousData());

        // out of value length
        settingResult = context.set(null, "123456789");
        Assert.assertNotNull(settingResult);
        Assert.assertNotNull(settingResult.errorMessage());
        Assert.assertNull(settingResult.previousData());
    }

    @Test
    public void testGet() {
        final CorrelationContext context = new CorrelationContext();
        context.set("test1", "t1");

        // manual get
        Assert.assertEquals("t1", context.get("test1"));
        // ket if null
        Assert.assertEquals("", context.get(null));
        // value if null
        context.set("test2", null);
        Assert.assertEquals("", context.get("test2"));
    }

    @Test
    public void testSerialize() {
        // manual
        CorrelationContext context = new CorrelationContext();
        context.set("test1", "t1");
        context.set("test2", "t2");
        Assert.assertEquals("dGVzdDE=:dDE=,dGVzdDI=:dDI=", context.serialize());

        // empty value
        context = new CorrelationContext();
        context.set("test1", null);
        Assert.assertEquals("dGVzdDE=:", context.serialize());

        // empty
        context = new CorrelationContext();
        Assert.assertEquals("", context.serialize());
    }

    @Test
    public void testDeserialize() {
        // manual
        CorrelationContext context = new CorrelationContext();
        context.deserialize("dGVzdDE=:dDE=,dGVzdDI=:dDI=");
        Assert.assertEquals("t1", context.get("test1"));
        Assert.assertEquals("t2", context.get("test2"));

        // empty value
        context = new CorrelationContext();
        context.deserialize("dGVzdDE=:");
        Assert.assertEquals("", context.get("test1"));

        // empty string
        context = new CorrelationContext();
        context.deserialize("");
        Assert.assertEquals("", context.get("test1"));
        context.deserialize(null);
        Assert.assertEquals("", context.get("test1"));
    }

}
