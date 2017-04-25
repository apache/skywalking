package com.a.eye.skywalking.collector.worker.config;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class HttpConfigProviderTestCase {

    @Test
    public void test() {
        HttpConfigProvider provider = new HttpConfigProvider();

        Assert.assertEquals(HttpConfig.class, provider.configClass());

        System.setProperty("http.HOSTNAME", "A");
        System.setProperty("http.PORT", "B");
        System.setProperty("http.CONTEXTPATH", "C");
        provider.cliArgs();

        Assert.assertEquals("A", HttpConfig.Http.HOSTNAME);
        Assert.assertEquals("B", HttpConfig.Http.PORT);
        Assert.assertEquals("C", HttpConfig.Http.CONTEXTPATH);
    }
}
