package org.apache.skywalking.apm.collector.ui.query;

import org.apache.skywalking.apm.collector.storage.ui.config.ExistedAlarmThresholds;
import org.apache.skywalking.apm.collector.storage.ui.config.ExistedTTLConfigs;
import org.junit.Assert;
import org.junit.Test;

/**
 * target code may not implement yet
 *
 * @author lican
 * @date 2018/4/13
 */
public class ConfigQueryTest {


    @Test
    public void queryAllDataTTLConfigs() {
        ConfigQuery configQuery = new ConfigQuery();
        ExistedTTLConfigs existedTTLConfigs = configQuery.queryAllDataTTLConfigs();
        Assert.assertNull(existedTTLConfigs);
    }

    @Test
    public void queryAlarmThresholds() {
        ConfigQuery configQuery = new ConfigQuery();
        ExistedAlarmThresholds existedAlarmThresholds = configQuery.queryAlarmThresholds(null);
        Assert.assertNull(existedAlarmThresholds);
    }
}