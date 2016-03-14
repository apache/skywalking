package test.ai.cloud.plugin;

import java.lang.reflect.InvocationTargetException;

import org.junit.Test;

import com.ai.cloud.skywalking.plugin.TracingBootstrap;

public class PluginMainTest {
	@Test
	public void testMain() throws IllegalAccessException,
			IllegalArgumentException, InvocationTargetException,
			NoSuchMethodException, SecurityException, ClassNotFoundException {
		TracingBootstrap
				.main(new String[] { "test.ai.cloud.plugin.PluginMainTest" });
	}

	public static void main(String[] args) {
		long start = System.currentTimeMillis();

		BeInterceptedClass inst = new BeInterceptedClass();
		inst.printabc();
		long end = System.currentTimeMillis();
		System.out.println(end - start + "ms");
	}
}
