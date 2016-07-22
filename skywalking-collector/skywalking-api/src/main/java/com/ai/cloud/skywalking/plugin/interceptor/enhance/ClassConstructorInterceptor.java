package com.ai.cloud.skywalking.plugin.interceptor.enhance;

import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.FieldProxy;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;

import com.ai.cloud.skywalking.plugin.interceptor.EnhancedClassInstanceContext;

public class ClassConstructorInterceptor {
	private static Logger logger = LogManager
			.getLogger(ClassConstructorInterceptor.class);

	private InstanceMethodsAroundInterceptor interceptor;

	public ClassConstructorInterceptor(InstanceMethodsAroundInterceptor interceptor) {
		this.interceptor = interceptor;
	}

	@RuntimeType
	public void intercept(
			@This Object obj,
			@FieldProxy(ClassEnhancePluginDefine.contextAttrName) FieldSetter accessor,
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
