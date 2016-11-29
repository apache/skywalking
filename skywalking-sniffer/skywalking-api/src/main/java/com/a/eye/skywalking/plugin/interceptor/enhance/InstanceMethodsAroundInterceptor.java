package com.a.eye.skywalking.plugin.interceptor.enhance;

import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;

public interface InstanceMethodsAroundInterceptor {
	void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, MethodInterceptResult result);
	
	Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, Object ret);
	
	void handleMethodException(Throwable t, EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext);
}
