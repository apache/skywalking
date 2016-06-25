package com.ai.cloud.skywalking.plugin.interceptor.enhance;

import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;

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
	protected StaticMethodsAroundInterceptor getStaticMethodsInterceptor() {
		return null;
	}

}
