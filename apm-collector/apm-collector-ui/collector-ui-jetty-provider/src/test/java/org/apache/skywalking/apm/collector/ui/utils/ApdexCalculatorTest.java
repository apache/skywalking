package org.apache.skywalking.apm.collector.ui.utils;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author lican
 * @date 2018/4/13
 */
public class ApdexCalculatorTest {

    /**
     * about apdex: https://en.wikipedia.org/wiki/Apdex
     */
    @Test
    public void testApdexCalculator() {
        int apdex = ApdexCalculator.INSTANCE.calculate(80, 10, 10);
        Assert.assertEquals(apdex, 85, 1);
    }
}
