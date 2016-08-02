package com.ai.cloud.skywalking.httpClient.v4.plugin.define;

import com.ai.cloud.skywalking.httpClient.v4.plugin.HttpClientExecuteInterceptor;

import com.ai.cloud.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;

public abstract class HttpClientPluginDefine extends ClassInstanceMethodsEnhancePluginDefine {

	@Override
	public String getInstanceMethodsInterceptor() {
		return "com.ai.cloud.skywalking.httpClient.v4.plugin.HttpClientExecuteInterceptor";
	}

}
