package com.ai.cloud.skywalking.plugin.interceptor;

import java.util.Set;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.pool.TypePool;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ai.cloud.skywalking.plugin.PluginCfg;

public class EnhanceClazz4Interceptor {
	private static Logger logger = LogManager
			.getLogger(EnhanceClazz4Interceptor.class);

	private TypePool typePool;

	public EnhanceClazz4Interceptor() {
		typePool = TypePool.Default.ofClassPath();
	}

	public void enhance() {
		Set<String> interceptorClassList = PluginCfg.CFG
				.getInterceptorClassList();

		for (String interceptorClassName : interceptorClassList) {
			try {
				enhance0(interceptorClassName);
			} catch (Throwable t) {
				logger.error("enhance class [{}] for intercept failure.",
						interceptorClassName, t);
			}
		}
	}

	private void enhance0(String enhanceOriginClassName) {
		/**
		 * add '$$Origin' at the end of be enhanced classname <br/>
		 * such as: class com.ai.cloud.TestClass to class com.ai.cloud.TestClass$$Origin
		 */
		new ByteBuddy()
				.redefine(typePool.describe(enhanceOriginClassName).resolve(),
						ClassFileLocator.ForClassLoader.ofClassPath())
				.name(enhanceOriginClassName + "$$Origin")
				.make()
				.load(ClassLoader.getSystemClassLoader(),
						ClassLoadingStrategy.Default.INJECTION).getLoaded();
	}
}
