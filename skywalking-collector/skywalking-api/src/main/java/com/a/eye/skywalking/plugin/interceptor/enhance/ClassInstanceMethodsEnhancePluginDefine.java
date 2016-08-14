package com.a.eye.skywalking.plugin.interceptor.enhance;

import com.a.eye.skywalking.plugin.interceptor.MethodMatcher;

/**
 * 仅增强拦截实例方法
 * 
 * @author wusheng
 *
 */
public abstract class ClassInstanceMethodsEnhancePluginDefine extends
		ClassEnhancePluginDefine {

	@Override
	protected MethodMatcher[] getStaticMethodsMatchers() {
		return null;
	}

	@Override
	protected String getStaticMethodsInterceptor() {
		return null;
	}

}
