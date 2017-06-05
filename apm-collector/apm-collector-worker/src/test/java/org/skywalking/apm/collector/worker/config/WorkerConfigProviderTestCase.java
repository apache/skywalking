package org.skywalking.apm.collector.worker.config;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class WorkerConfigProviderTestCase {

    @Test
    public void test() {
        WorkerConfigProvider provider = new WorkerConfigProvider();
        provider.cliArgs();

        Assert.assertEquals(WorkerConfig.class, provider.configClass());
    }
}
