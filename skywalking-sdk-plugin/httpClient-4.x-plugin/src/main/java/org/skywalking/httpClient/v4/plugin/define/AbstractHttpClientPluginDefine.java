package org.skywalking.httpClient.v4.plugin.define;

import com.ai.cloud.skywalking.plugin.interceptor.InterceptPoint;
import com.ai.cloud.skywalking.plugin.interceptor.MethodNameMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.FullNameMatcher;

public class AbstractHttpClientPluginDefine extends HttpClientPluginDefine {

	@Override
	public String getBeInterceptedClassName() {
		return "org.apache.http.impl.client.AbstractHttpClient";
	}

	/**
	 * version 4.2, intercept method: execute, intercept<br/>
	 * public final HttpResponse execute(HttpHost target, HttpRequest request,
	 * HttpContext context)<br/>
	 * 
	 */
	@Override
	public MethodNameMatcher[] getBeInterceptedMethods() {
		return new MethodNameMatcher[] {
				new FullNameMatcher("doExecute")};
	}
}
