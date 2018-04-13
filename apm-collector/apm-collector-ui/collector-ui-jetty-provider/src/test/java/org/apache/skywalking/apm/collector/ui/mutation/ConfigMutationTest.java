package org.apache.skywalking.apm.collector.ui.mutation;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * this class may not be implemented ,so just test if it's null
 * if update the class ,please update the testcase
 * @author lican
 * @date 2018/4/13
 */
public class ConfigMutationTest {

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void setDataTTLConfigs() {
        ConfigMutation configMutation = new ConfigMutation();
        Boolean aBoolean = configMutation.setDataTTLConfigs(null);
        Assert.assertNull(aBoolean);
    }

    @Test
    public void setAlarmThreshold() {
        ConfigMutation configMutation = new ConfigMutation();
        Boolean aBoolean = configMutation.setAlarmThreshold(null);
        Assert.assertNull(aBoolean);
    }
}