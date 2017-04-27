package org.skywalking.apm.collector.worker.config;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class CacheSizeConfigTestCase {

    @Test
    public void test() {
        Assert.assertEquals(1024, CacheSizeConfig.Cache.Analysis.SIZE);
        Assert.assertEquals(5000, CacheSizeConfig.Cache.Persistence.SIZE);
    }
}
