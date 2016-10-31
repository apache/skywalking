package com.a.eye.skywalking.plugin.httpClient.v4.define;

import com.a.eye.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;
import com.a.eye.skywalking.plugin.interceptor.MethodMatcher;

public class AbstractHttpClientPluginDefine extends HttpClientPluginDefine {

	@Override
	public String enhanceClassName() {
		return "org.apache.http.impl.client.AbstractHttpClient";
	}

	/**
	 * version 4.2, intercept method: execute, intercept<br/>
	 * public final HttpResponse execute(HttpHost target, HttpRequest request,
	 * HttpContext context)<br/>
	 * 
	 */
	@Override
	public MethodMatcher[] getInstanceMethodsMatchers() {
		return new MethodMatcher[] {
				new SimpleMethodMatcher("doExecute")};
	}
}
