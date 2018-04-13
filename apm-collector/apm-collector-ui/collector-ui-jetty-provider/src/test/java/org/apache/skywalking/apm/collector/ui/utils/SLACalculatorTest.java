package org.apache.skywalking.apm.collector.ui.utils;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author lican
 */
public class SLACalculatorTest {

    @Test
    public void testCalculate() {
        int calculate = SLACalculator.INSTANCE.calculate(20, 100);
        Assert.assertEquals(80, calculate);
    }
}
