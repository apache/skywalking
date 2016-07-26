package test.ai.cloud.matcher;

import junit.framework.TestCase;

import org.junit.Test;

import com.ai.cloud.skywalking.plugin.PluginBootstrap;

public class ExclusionMatcherTest extends TestCase{
	@Test
    public void testMatcher() throws ClassNotFoundException, IllegalAccessException, InstantiationException, InterruptedException {
        //new PluginBootstrap().start();
        TestMatcherClass testMatcherClass = (TestMatcherClass) Class.forName("sample.ai.cloud.matcher.TestMatcherClass").newInstance();

        testMatcherClass.set();
        testMatcherClass.seta("a");
        testMatcherClass.get("a");
        testMatcherClass.find();
        System.out.println(testMatcherClass.toString());
        testMatcherClass.equals(new TestMatcherClass());
    }

}
