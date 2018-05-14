package org.apache.skywalking.apm.plugin.trace.ignore;

import org.apache.skywalking.apm.plugin.trace.ignore.matcher.AntPathMatcher;
import org.apache.skywalking.apm.plugin.trace.ignore.matcher.TracePathMatcher;
import org.junit.Assert;
import org.junit.Test;

public class TracePathMatcherTest {

    @Test
    public void testAntPathMatcher() {
        TracePathMatcher pathMatcher = new AntPathMatcher();
        String patten = "/eureka/*";
        String path = "/eureka/app";

        boolean match = pathMatcher.match(patten, path);
        Assert.assertTrue(match);

        path = "/eureka/apps/list";
        match = pathMatcher.match(patten, path);
        Assert.assertFalse(match);

        patten = "/eureka/**";
        match = pathMatcher.match(patten, path);
        Assert.assertTrue(match);

        patten = "/eureka/apps/lis?";
        match = pathMatcher.match(patten, path);
        Assert.assertTrue(match);

        path = "eureka/apps/lists";
        match = pathMatcher.match(patten, path);
        Assert.assertFalse(match);
    }
}
