package test.ai.cloud.plugin;

import java.lang.reflect.InvocationTargetException;

import com.ai.cloud.skywalking.plugin.TracingBootstrap;

public class PluginMainTest {
	public void testMain() throws IllegalAccessException,
			IllegalArgumentException, InvocationTargetException,
			NoSuchMethodException, SecurityException, ClassNotFoundException {
		TracingBootstrap
				.main(new String[] { "test.ai.cloud.plugin.PluginMainTest" });
	}

	public static void main(String[] args) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		long start = System.currentTimeMillis();

		
		BeInterceptedClass inst = (BeInterceptedClass) Class.forName("test.ai.cloud.plugin.BeInterceptedClass").newInstance();
		inst.printabc();
		long end = System.currentTimeMillis();
		System.out.println(end - start + "ms");
	}
}
