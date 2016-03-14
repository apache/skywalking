package com.ai.cloud.skywalking.plugin.interceptor;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import java.util.Set;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bind.annotation.FieldProxy;
import net.bytebuddy.pool.TypePool;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ai.cloud.skywalking.plugin.PluginCfg;

public class EnhanceClazz4Interceptor {
	private static Logger logger = LogManager
			.getLogger(EnhanceClazz4Interceptor.class);

	private TypePool typePool;

	public static final String contextAttrName = "_$EnhancedClassInstanceContext";

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

	private void enhance0(String interceptorDefineClassName)
			throws InstantiationException, IllegalAccessException,
			ClassNotFoundException {
		logger.debug("prepare to enhance class by {}.",
				interceptorDefineClassName);
		InterceptorDefine define = (InterceptorDefine) Class.forName(
				interceptorDefineClassName).newInstance();

		String enhanceOriginClassName = define.getBeInterceptedClassName();

		logger.debug("prepare to enhance class {} by {}.",
				enhanceOriginClassName, interceptorDefineClassName);
		/**
		 * rename origin class <br/>
		 * add '$$Origin' at the end of be enhanced classname <br/>
		 * such as: class com.ai.cloud.TestClass to class
		 * com.ai.cloud.TestClass$$Origin
		 */
		String renameClassName = enhanceOriginClassName + "$$Origin";
		Class<?> originClass = new ByteBuddy()
				.redefine(typePool.describe(enhanceOriginClassName).resolve(),
						ClassFileLocator.ForClassLoader.ofClassPath())
				.name(renameClassName)
				.make()
				.load(ClassLoader.getSystemClassLoader(),
						ClassLoadingStrategy.Default.INJECTION).getLoaded();

		/**
		 * create a new class using origin classname.<br/>
		 * 
		 * new class need:<br/>
		 * 1.add field '_$EnhancedClassInstanceContext' of type
		 * EnhancedClassInstanceContext <br/>
		 * 
		 * 2.intercept constructor by default, and intercept method which it's required by
		 * interceptorDefineClass. <br/>
		 */
		IAroundInterceptor interceptor = define.instance();

		DynamicType.Builder<?> newClassBuilder = new ByteBuddy().subclass(
				originClass, ConstructorStrategy.Default.IMITATE_SUPER_CLASS);
		newClassBuilder = newClassBuilder
				.defineField(contextAttrName,
						EnhancedClassInstanceContext.class)
				.constructor(isConstructor())
				.intercept(
						SuperMethodCall.INSTANCE.andThen(MethodDelegation.to(
								new ClassConstructorInterceptor(interceptor))
								.appendParameterBinder(
										FieldProxy.Binder.install(
												FieldGetter.class,
												FieldSetter.class))));

		String[] methodNameList = define.getBeInterceptedMethods();
		for (String methodName : methodNameList) {
			newClassBuilder = newClassBuilder.method(named(methodName))
					.intercept(
							MethodDelegation.to(new ClassMethodInterceptor(
									interceptor)));
		}

		/**
		 * naming class as origin class name, make and load class to
		 * classloader.
		 */
		newClassBuilder
				.name(enhanceOriginClassName)
				.make()
				.load(ClassLoader.getSystemClassLoader(),
						ClassLoadingStrategy.Default.INJECTION).getLoaded();

		logger.debug("enhance class {} by {} completely.",
				enhanceOriginClassName, interceptorDefineClassName);
	}
}
