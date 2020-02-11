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

package org.apache.skywalking.apm.plugin.spring.mvc.v4;

import java.lang.reflect.Method;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.PathMappingCache;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class PathMappingCacheTest {
    private PathMappingCache pathMappingCache;

    @Before
    public void setUp() throws Exception {
        pathMappingCache = new PathMappingCache("org.apache.skywalking.apm.plugin.spring.mvc");
    }

    @Test
    public void testAddPathMapping1() throws Throwable {
        Object obj = new Object();
        Method m = obj.getClass().getMethods()[0];
        pathMappingCache.addPathMapping(m, "#toString");

        Assert.assertEquals("the two value should be equal", pathMappingCache.findPathMapping(m), "/org.apache.skywalking.apm.plugin.spring.mvc/#toString");

    }

    @Test
    public void testAutoAddPathSeparator() {
        String rightPath = "/root/sub";

        Object obj = new Object();
        Method m = obj.getClass().getMethods()[0];

        PathMappingCache cache = new PathMappingCache("root");
        cache.addPathMapping(m, "sub");
        Assert.assertEquals(cache.findPathMapping(m), rightPath);

        PathMappingCache cache2 = new PathMappingCache("/root");
        cache2.addPathMapping(m, "/sub");
        Assert.assertEquals(cache2.findPathMapping(m), rightPath);

        PathMappingCache cache3 = new PathMappingCache("root");
        cache3.addPathMapping(m, "/sub");
        Assert.assertEquals(cache3.findPathMapping(m), rightPath);

        PathMappingCache cache4 = new PathMappingCache("/root");
        cache4.addPathMapping(m, "sub");
        Assert.assertEquals(cache4.findPathMapping(m), rightPath);
    }
}
