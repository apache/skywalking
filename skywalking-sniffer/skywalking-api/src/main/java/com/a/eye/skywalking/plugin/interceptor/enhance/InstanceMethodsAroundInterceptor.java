package com.a.eye.skywalking.plugin.interceptor.enhance;

import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;

/**
 * A interceptor, which intercept method's invocation.
 * The target methods will be defined in {@link ClassEnhancePluginDefine}'s subclass, most likely in {@link ClassInstanceMethodsEnhancePluginDefine}
 *
 * @author wusheng
 */
public interface InstanceMethodsAroundInterceptor {
	/**
	 * called before target method invocation.
	 * @param context instance context, a class instance only has one {@link EnhancedClassInstanceContext} instance.
	 * @param interceptorContext method context, includes class name, method name, etc.
	 * @param result change this result, if you want to truncate the method.
	 */
	void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, MethodInterceptResult result);

	/**
	 * called after target method invocation. Even method's invocation triggers an exception.
	 * @param context instance context, a class instance only has one {@link EnhancedClassInstanceContext} instance.
	 * @param interceptorContext method context, includes class name, method name, etc.
	 * @param ret the method's original return value.
	 * @return the method's actual return value.
	 */
	Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, Object ret);

	/**
	 * called when occur exception.
	 * @param t the exception occur.
	 * @param context  instance context, a class instance only has one {@link EnhancedClassInstanceContext} instance.
	 * @param interceptorContext method context, includes class name, method name, etc.
	 */
	void handleMethodException(Throwable t, EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext);
}
