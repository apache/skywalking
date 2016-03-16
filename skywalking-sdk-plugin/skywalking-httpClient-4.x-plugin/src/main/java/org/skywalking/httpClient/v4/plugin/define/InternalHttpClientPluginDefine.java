package org.skywalking.httpClient.v4.plugin.define;

import com.ai.cloud.skywalking.plugin.interceptor.InterceptPoint;

public class InternalHttpClientPluginDefine extends HttpClientPluginDefine {
	@Override
	public InterceptPoint[] getBeInterceptedMethods() {
		return new InterceptPoint[]{new InterceptPoint("doExecute")};
	}
	
	@Override
	public String getBeInterceptedClassName() {
		return "org.apache.http.impl.client.InternalHttpClient";
	}

}
