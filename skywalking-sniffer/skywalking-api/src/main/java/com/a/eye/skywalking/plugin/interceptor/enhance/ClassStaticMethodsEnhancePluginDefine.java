package com.a.eye.skywalking.plugin.interceptor.enhance;

import com.a.eye.skywalking.plugin.interceptor.MethodMatcher;

/**
 * 仅增强拦截类级别静态方法
 * 
 * @author wusheng
 *
 */
public abstract class ClassStaticMethodsEnhancePluginDefine extends
		ClassEnhancePluginDefine {

	@Override
	protected MethodMatcher[] getInstanceMethodsMatchers() {
		return null;
	}

	@Override
	protected String getInstanceMethodsInterceptor() {
		return null;
	}
}
