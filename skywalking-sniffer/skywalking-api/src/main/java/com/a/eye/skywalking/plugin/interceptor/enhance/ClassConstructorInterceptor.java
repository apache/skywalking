package com.a.eye.skywalking.plugin.interceptor.enhance;

import com.a.eye.skywalking.logging.LogManager;
import com.a.eye.skywalking.logging.EasyLogger;
import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.loader.InterceptorInstanceLoader;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.FieldProxy;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;

public class ClassConstructorInterceptor {
	private static EasyLogger easyLogger = LogManager
			.getLogger(ClassConstructorInterceptor.class);

	private String instanceMethodsAroundInterceptorClassName;

	public ClassConstructorInterceptor(String instanceMethodsAroundInterceptorClassName) {
		this.instanceMethodsAroundInterceptorClassName = instanceMethodsAroundInterceptorClassName;
	}

	@RuntimeType
	public void intercept(
			@This Object obj,
			@FieldProxy(ClassEnhancePluginDefine.contextAttrName) FieldSetter accessor,
			@AllArguments Object[] allArguments) {
		try {
			InstanceMethodsAroundInterceptor interceptor = InterceptorInstanceLoader
					.load(instanceMethodsAroundInterceptorClassName, obj.getClass().getClassLoader());

			EnhancedClassInstanceContext context = new EnhancedClassInstanceContext();
			accessor.setValue(context);
			ConstructorInvokeContext interceptorContext = new ConstructorInvokeContext(obj,
					allArguments);
			interceptor.onConstruct(context, interceptorContext);
		} catch (Throwable t) {
			easyLogger.error("ClassConstructorInterceptor failue.", t);
		}

	}
}
