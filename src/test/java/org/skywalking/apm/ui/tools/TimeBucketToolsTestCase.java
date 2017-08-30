package org.skywalking.apm.ui.tools;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class TimeBucketToolsTestCase {

    @Test
    public void testBuildXAxis() {
        String time = "201703250918";
        String value = TimeBucketTools.buildXAxis(TimeBucketTools.Type.MINUTE.name(), time);
        Assert.assertEquals("09:18", value);

        value = TimeBucketTools.buildXAxis(TimeBucketTools.Type.HOUR.name(), time);
        Assert.assertEquals("25 09", value);

        value = TimeBucketTools.buildXAxis(TimeBucketTools.Type.DAY.name(), time);
        Assert.assertEquals("03-25", value);
    }
}
