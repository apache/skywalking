package com.a.eye.skywalking.collector.worker.config;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class HttpConfigTestCase {

    @Test
    public void test() {
        Assert.assertEquals("", HttpConfig.Http.HOSTNAME);
        Assert.assertEquals("", HttpConfig.Http.PORT);
        Assert.assertEquals("", HttpConfig.Http.CONTEXTPATH);
    }
}
