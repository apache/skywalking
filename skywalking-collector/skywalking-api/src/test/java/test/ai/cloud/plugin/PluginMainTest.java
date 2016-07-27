package test.ai.cloud.plugin;

import com.ai.cloud.skywalking.plugin.PluginException;
import com.ai.cloud.skywalking.plugin.TracingBootstrap;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;

public class PluginMainTest {
    @Test
    public void testMain() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, PluginException {
        TracingBootstrap.main(new String[] {"test.ai.cloud.plugin.PluginMainTest"});
    }

    public static void main(String[] args)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException,
            SecurityException {
        long start = System.currentTimeMillis();

        BeInterceptedClass inst = (BeInterceptedClass) Class.forName("test.ai.cloud.plugin.BeInterceptedClass").newInstance();
        inst.printabc();
        long end = System.currentTimeMillis();
        System.out.println(end - start + "ms");

        BeInterceptedClass.call();
    }
}
