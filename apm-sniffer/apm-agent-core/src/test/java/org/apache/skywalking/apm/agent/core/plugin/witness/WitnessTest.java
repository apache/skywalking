package org.apache.skywalking.apm.agent.core.plugin.witness;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.skywalking.apm.agent.core.plugin.WitnessFinder;
import org.apache.skywalking.apm.agent.core.plugin.WitnessMethod;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;

/**
 * witness test
 */
public class WitnessTest {

    private String className = "org.apache.skywalking.apm.agent.core.plugin.witness.WitnessTest";

    @Test
    public void testWitnessClass(){
        Assert.assertTrue(WitnessFinder.exist(className, this.getClass().getClassLoader()));
    }

    @Test
    public void testWitnessMethod(){
        ElementMatcher.Junction<MethodDescription> junction = ElementMatchers.named("foo")
                .and(ElementMatchers.returnsGeneric(target -> "java.util.List<java.util.Map<java.lang.String, java.lang.Object>>".equals(target.getTypeName())))
                .and(ElementMatchers.takesGenericArgument(0, target -> "java.util.List<java.util.Map<java.lang.String, java.lang.Object>>".equals(target.getTypeName())))
                .and(ElementMatchers.takesArgument(1, target -> "java.lang.String".equals(target.getName())));
        WitnessMethod witnessMethod = new WitnessMethod(className, junction);
        Assert.assertTrue(WitnessFinder.exist(witnessMethod, this.getClass().getClassLoader()));
    }

    public List<Map<String, Object>> foo(List<Map<String, Object>> param, String s) {
        return null;
    }

}
