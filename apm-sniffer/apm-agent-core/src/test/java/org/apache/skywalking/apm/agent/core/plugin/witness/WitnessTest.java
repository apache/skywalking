package org.apache.skywalking.apm.agent.core.plugin.witness;

import net.bytebuddy.matcher.ElementMatchers;
import org.apache.skywalking.apm.agent.core.plugin.WitnessFinder;
import org.apache.skywalking.apm.agent.core.plugin.WitnessMethod;
import org.junit.Assert;
import org.junit.Test;

/**
 * witness test
 */
public class WitnessTest {

    private String className = "org.apache.skywalking.apm.agent.core.plugin.witness.WitnessTest";

    @Test
    public void testWitnessClass(){
        Assert.assertTrue(WitnessFinder.INSTANCE.exist(className, this.getClass().getClassLoader()));
    }

    @Test
    public void testWitnessMethod(){
        WitnessMethod witnessMethod = new WitnessMethod(className, ElementMatchers.named("testWitnessMethod"));
        Assert.assertTrue(WitnessFinder.INSTANCE.exist(witnessMethod, this.getClass().getClassLoader()));
    }

}
