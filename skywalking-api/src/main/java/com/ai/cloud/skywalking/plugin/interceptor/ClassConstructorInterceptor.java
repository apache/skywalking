package com.ai.cloud.skywalking.plugin.interceptor;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.FieldProxy;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ClassConstructorInterceptor {
	private static Logger logger = LogManager
			.getLogger(ClassConstructorInterceptor.class);

	private IAroundInterceptor interceptor;

	public ClassConstructorInterceptor(IAroundInterceptor interceptor) {
		this.interceptor = interceptor;
	}

	@RuntimeType
	public void intercept(
			@This Object obj,
			@FieldProxy(InterceptorPluginDefine.contextAttrName) FieldSetter accessor,
			@AllArguments Object[] allArguments) {
		try {
			EnhancedClassInstanceContext context = new EnhancedClassInstanceContext();
			accessor.setValue(context);
			ConstructorInvokeContext interceptorContext = new ConstructorInvokeContext(obj,
					allArguments);
			interceptor.onConstruct(context, interceptorContext);
		} catch (Throwable t) {
			logger.error("ClassConstructorInterceptor failue.", t);
		}

	}
}
