package test.ai.cloud.matcher;

import com.ai.cloud.skywalking.plugin.AbstractClassEnhancePluginDefine;
import com.ai.cloud.skywalking.plugin.PluginBootstrap;
import com.ai.cloud.skywalking.plugin.PluginDefineCategory;
import com.ai.cloud.skywalking.plugin.PluginException;
import junit.framework.TestCase;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.pool.TypePool;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class ExclusionMatcherTest extends TestCase {
    @Test
    public void testMatcher()
            throws ClassNotFoundException, IllegalAccessException, InstantiationException, InterruptedException,
            PluginException {
        List<AbstractClassEnhancePluginDefine> pluginDefines = new PluginBootstrap().loadPlugins();

        PluginDefineCategory category = PluginDefineCategory.category(pluginDefines);


        for (Map.Entry<String, AbstractClassEnhancePluginDefine> entry : category
                .getExactClassEnhancePluginDefineMapping().entrySet()) {
            DynamicType.Builder<?> newClassBuilder = new ByteBuddy()
                    .rebase(TypePool.Default.ofClassPath().describe(entry.getKey()).resolve(),
                            ClassFileLocator.ForClassLoader.ofClassPath());

            newClassBuilder = entry.getValue().define(entry.getKey(), newClassBuilder);
            newClassBuilder.make().load(ClassLoader.getSystemClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                    .getLoaded();
        }

        TestMatcherClass testMatcherClass =
                (TestMatcherClass) Class.forName("test.ai.cloud.matcher.TestMatcherClass").newInstance();

        testMatcherClass.set();
        testMatcherClass.seta("a");
        testMatcherClass.get("a");
        testMatcherClass.find();
        System.out.println(testMatcherClass.toString());
        testMatcherClass.equals(new TestMatcherClass());
    }

}
