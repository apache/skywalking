package org.skywalking.apm.ui.tools;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class TimeToolsTestCase {

    @Test
    public void testBuildXAxis() {
        String time = "201703250918";
        String value = TimeTools.buildXAxis(TimeTools.Minute, time);
        Assert.assertEquals("09:18", value);

        value = TimeTools.buildXAxis(TimeTools.Hour, time);
        Assert.assertEquals("25 09", value);

        value = TimeTools.buildXAxis(TimeTools.Day, time);
        Assert.assertEquals("03-25", value);
    }
}
