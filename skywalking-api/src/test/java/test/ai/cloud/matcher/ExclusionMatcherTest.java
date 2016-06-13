package test.ai.cloud.matcher;

import junit.framework.TestCase;

import com.ai.cloud.skywalking.plugin.PluginBootstrap;

public class ExclusionMatcherTest extends TestCase{

    public void testMatcher() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        new PluginBootstrap().start();
        TestMatcherClass testMatcherClass = (TestMatcherClass) Class.forName("test.ai.cloud.matcher.TestMatcherClass").newInstance();

        testMatcherClass.set();
        testMatcherClass.seta("a");
        testMatcherClass.get("a");
        testMatcherClass.find();
        System.out.println(testMatcherClass.toString());
    }

}
