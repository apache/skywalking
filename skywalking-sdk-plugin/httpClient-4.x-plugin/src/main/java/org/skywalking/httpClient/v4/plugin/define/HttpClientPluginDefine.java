package org.skywalking.httpClient.v4.plugin.define;

import org.skywalking.httpClient.v4.plugin.HttpClientExecuteInterceptor;

import com.ai.cloud.skywalking.plugin.interceptor.IAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.InterceptorDefine;

public abstract class HttpClientPluginDefine implements InterceptorDefine {

	@Override
	public IAroundInterceptor instance() {
		return new HttpClientExecuteInterceptor();
	}

}
