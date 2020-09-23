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

public class ExceptionCheckStrategyTest {

    @Before
    public void prepare() {
        Config.StatusCheck.IGNORED_EXCEPTIONS = "org.apache.skywalking.apm.agent.core.context.status.TestNamedMatchException";
        Config.StatusCheck.MAX_RECURSIVE_DEPTH = 1;
        ServiceManager.INSTANCE.boot();
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
    public void checkOffExceptionCheckStrategy() {
        OffExceptionCheckStrategy offExceptionCheckStrategy = new OffExceptionCheckStrategy();
        Assert.assertTrue(offExceptionCheckStrategy.isError(new TestHierarchyMatchException()));
    }

    @Test
    public void checkInheriteMatchExceptionCheckStrategy() {
        HierarchyMatchExceptionCheckStrategy hierarchyMatchExceptionCheckStrategy = new HierarchyMatchExceptionCheckStrategy();
        Assert.assertFalse(hierarchyMatchExceptionCheckStrategy.isError(new TestNamedMatchException()));
        Assert.assertFalse(hierarchyMatchExceptionCheckStrategy.isError(new TestHierarchyMatchException()));
        Assert.assertTrue(hierarchyMatchExceptionCheckStrategy.isError(new TestAnnotationMatchException()));
    }

    @Test
    public void checkAnnotationMatchExceptionCheckStrategy() {
        AnnotationMatchExceptionCheckStrategy annotationMatchExceptionCheckStrategy = new AnnotationMatchExceptionCheckStrategy();
        Assert.assertFalse(annotationMatchExceptionCheckStrategy.isError(new TestAnnotationMatchException()));
        Assert.assertTrue(annotationMatchExceptionCheckStrategy.isError(new TestHierarchyMatchException()));
    }

}
