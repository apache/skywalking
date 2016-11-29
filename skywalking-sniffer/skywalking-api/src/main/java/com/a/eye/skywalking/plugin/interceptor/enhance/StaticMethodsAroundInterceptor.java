package com.a.eye.skywalking.plugin.interceptor.enhance;


public interface StaticMethodsAroundInterceptor {
	void beforeMethod(StaticMethodInvokeContext interceptorContext, MethodInterceptResult result);
	
	Object afterMethod(StaticMethodInvokeContext interceptorContext, Object ret);
	
	void handleMethodException(Throwable t, MethodInvokeContext interceptorContext);
}
