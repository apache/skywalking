package org.skywalking.httpClient.v4.plugin.define;

import org.skywalking.httpClient.v4.plugin.HttpClientExecuteInterceptor;

import com.ai.cloud.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.IntanceMethodsAroundInterceptor;

public abstract class HttpClientPluginDefine extends ClassInstanceMethodsEnhancePluginDefine {

	@Override
	public IntanceMethodsAroundInterceptor getInstanceMethodsInterceptor() {
		return new HttpClientExecuteInterceptor();
	}

}
