package com.a.eye.skywalking.collector.worker.config;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class CacheSizeConfigProviderTestCase {

    @Test
    public void test() {
        CacheSizeConfigProvider provider = new CacheSizeConfigProvider();
        provider.cliArgs();

        Assert.assertEquals(CacheSizeConfig.class, provider.configClass());
    }
}
