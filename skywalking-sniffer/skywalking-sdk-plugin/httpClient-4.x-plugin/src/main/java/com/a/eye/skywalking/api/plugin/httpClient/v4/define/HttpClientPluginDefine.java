package com.a.eye.skywalking.api.plugin.httpClient.v4.define;

import com.a.eye.skywalking.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;

public abstract class HttpClientPluginDefine extends ClassInstanceMethodsEnhancePluginDefine {
	@Override
	protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
		return null;
	}

	protected String getInstanceMethodsInterceptor() {
		return "com.a.eye.skywalking.plugin.httpClient.v4.HttpClientExecuteInterceptor";
	}
}
