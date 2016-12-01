package com.a.eye.skywalking.plugin.interceptor.enhance;

import com.a.eye.skywalking.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.InstanceMethodsInterceptPoint;

/**
 * 仅增强拦截类级别静态方法
 * 
 * @author wusheng
 *
 */
public abstract class ClassStaticMethodsEnhancePluginDefine extends
		ClassEnhancePluginDefine {

	@Override
	protected ConstructorInterceptPoint[] getConstructorsInterceptPoints(){
		return null;
	}

	@Override
	protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints(){
		return null;
	}
}
