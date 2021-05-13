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

package org.apache.skywalking.apm.plugin.trace.ignore;

import org.apache.skywalking.apm.plugin.trace.ignore.matcher.FastPathMatcher;
import org.apache.skywalking.apm.plugin.trace.ignore.matcher.TracePathMatcher;
import org.junit.Assert;
import org.junit.Test;

public class TracePathMatcherTest {

    @Test
    public void testAntPathMatcher() {
        TracePathMatcher pathMatcher = new FastPathMatcher();
        String patten = "/eureka/*";
        String path = "/eureka/apps";
        boolean match = pathMatcher.match(patten, path);
        Assert.assertTrue(match);
        path = "/eureka/";
        match = pathMatcher.match(patten, path);
        Assert.assertTrue(match);
        path = "/eureka/apps/";
        match = pathMatcher.match(patten, path);
        Assert.assertFalse(match);

        patten = "/eureka/*/";
        path = "/eureka/apps/";
        match = pathMatcher.match(patten, path);
        Assert.assertTrue(match);
        path = "/eureka/";
        match = pathMatcher.match(patten, path);
        Assert.assertFalse(match);
        path = "/eureka/apps/list";
        match = pathMatcher.match(patten, path);
        Assert.assertFalse(match);

        patten = "/eureka/**";
        path = "/eureka/";
        match = pathMatcher.match(patten, path);
        Assert.assertTrue(match);
        path = "/eureka/apps/test";
        match = pathMatcher.match(patten, path);
        Assert.assertTrue(match);
        path = "/eureka/apps/test/";
        match = pathMatcher.match(patten, path);
        Assert.assertFalse(match);

        patten = "eureka/apps/?";
        path = "eureka/apps/list";
        match = pathMatcher.match(patten, path);
        Assert.assertFalse(match);
        path = "eureka/apps/";
        match = pathMatcher.match(patten, path);
        Assert.assertFalse(match);
        path = "eureka/apps/a";
        match = pathMatcher.match(patten, path);
        Assert.assertTrue(match);

        patten = "eureka/**/lists";
        path = "eureka/apps/lists";
        match = pathMatcher.match(patten, path);
        Assert.assertTrue(match);
        path = "eureka/apps/test/lists";
        match = pathMatcher.match(patten, path);
        Assert.assertTrue(match);
        path = "eureka/apps/test/";
        match = pathMatcher.match(patten, path);
        Assert.assertFalse(match);
        path = "eureka/apps/test";
        match = pathMatcher.match(patten, path);
        Assert.assertFalse(match);

        patten = "eureka/**/test/**";
        path = "eureka/apps/test/list";
        match = pathMatcher.match(patten, path);
        Assert.assertTrue(match);
        path = "eureka/apps/foo/test/list/bar";
        match = pathMatcher.match(patten, path);
        Assert.assertTrue(match);
        path = "eureka/apps/foo/test/list/bar/";
        match = pathMatcher.match(patten, path);
        Assert.assertFalse(match);
        path = "eureka/apps/test/list";
        match = pathMatcher.match(patten, path);
        Assert.assertTrue(match);
        path = "eureka/test/list";
        match = pathMatcher.match(patten, path);
        Assert.assertFalse(match);

        patten = "/eureka/**/b/**/*.txt";
        path = "/eureka/a/aa/aaa/b/bb/bbb/xxxxxx.txt";
        match = pathMatcher.match(patten, path);
        Assert.assertTrue(match);
        path = "/eureka/a/aa/aaa/b/bb/bbb/xxxxxx";
        match = pathMatcher.match(patten, path);
        Assert.assertFalse(match);
    }
}
