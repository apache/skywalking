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

package org.apache.skywalking.apm.agent.core.context.status;

import java.util.Set;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.powermock.api.support.membermodification.MemberModifier;

public class StatusCheckServiceCheckTest {

    private Exception exception1;
    private Exception exception2;
    private Exception exception3;

    @Before
    public void prepare() {
        Config.StatusCheck.IGNORED_EXCEPTIONS = "org.apache.skywalking.apm.agent.core.context.status.TestNamedMatchException";
        Config.StatusCheck.MAX_RECURSIVE_DEPTH = 1;
        ServiceManager.INSTANCE.boot();
        exception1 = new TestNamedMatchException();
        exception2 = new IllegalArgumentException(exception1);
        exception3 = new IllegalArgumentException(exception2);
    }

    @After
    public void after() throws IllegalAccessException {
        ((Set) MemberModifier
            .field(ExceptionCheckContext.class, "ignoredExceptions")
            .get(ExceptionCheckContext.INSTANCE)).clear();
        ((Set) MemberModifier
            .field(ExceptionCheckContext.class, "errorStatusExceptions")
            .get(ExceptionCheckContext.INSTANCE)).clear();
    }

    @Test
    public void testDepth_1() {
        Config.StatusCheck.MAX_RECURSIVE_DEPTH = 1;
        StatusCheckService service = ServiceManager.INSTANCE.findService(StatusCheckService.class);
        Assert.assertFalse(service.isError(exception1));
        Assert.assertTrue(service.isError(exception2));
        Assert.assertTrue(service.isError(exception3));
    }

    @Test
    public void testDepth_2() {
        Config.StatusCheck.MAX_RECURSIVE_DEPTH = 2;
        StatusCheckService service = ServiceManager.INSTANCE.findService(StatusCheckService.class);
        Assert.assertFalse(service.isError(exception1));
        Assert.assertFalse(service.isError(exception2));
        Assert.assertTrue(service.isError(exception3));
    }

    @Test
    public void testDepth_3() {
        Config.StatusCheck.MAX_RECURSIVE_DEPTH = 3;
        StatusCheckService service = ServiceManager.INSTANCE.findService(StatusCheckService.class);
        Assert.assertFalse(service.isError(exception1));
        Assert.assertFalse(service.isError(exception2));
        Assert.assertFalse(service.isError(exception3));
    }

}
