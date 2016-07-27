package test.ai.cloud.matcher;

import com.ai.cloud.skywalking.plugin.*;
import junit.framework.TestCase;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.pool.TypePool;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class ExclusionMatcherTest extends TestCase {
    @Test
    public void testMatcher() throws ClassNotFoundException, IllegalAccessException, InstantiationException, InterruptedException, PluginException {
        List<IPlugin> pluginDefines = new PluginBootstrap().loadPlugins();

       PluginDefineCategory category = PluginDefineCategory.category(pluginDefines);


        for (Map.Entry<String, AbstractClassEnhancePluginDefine> entry : category.getClassEnhancePluginDefines().entrySet()) {
            DynamicType.Builder<?> newClassBuilder =
                    new ByteBuddy().rebase(TypePool.Default.ofClassPath().describe(entry.getKey()).resolve(), ClassFileLocator.ForClassLoader.ofClassPath());

            entry.getValue().define(newClassBuilder);
        }

        TestMatcherClass testMatcherClass = (TestMatcherClass) Class.forName("test.ai.cloud.matcher.TestMatcherClass").newInstance();

        testMatcherClass.set();
        testMatcherClass.seta("a");
        testMatcherClass.get("a");
        testMatcherClass.find();
        System.out.println(testMatcherClass.toString());
        testMatcherClass.equals(new TestMatcherClass());
    }

}
