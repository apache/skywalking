package org.apache.skywalking.apm.agent.core.logging.core;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author alvin
 */
public class PatternLogResolverTest {

    @Test
    public void testGetLogger() {
        Assert.assertTrue(new PatternLogResolver().getLogger(PatternLoggerTest.class) instanceof PatternLogger);
    }

}