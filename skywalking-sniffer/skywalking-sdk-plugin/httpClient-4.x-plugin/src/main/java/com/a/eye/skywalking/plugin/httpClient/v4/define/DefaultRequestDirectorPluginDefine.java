package com.a.eye.skywalking.plugin.httpClient.v4.define;

import com.a.eye.skywalking.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.MethodMatcher;
import com.a.eye.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;

public class DefaultRequestDirectorPluginDefine extends HttpClientPluginDefine {
	/**
	 * DefaultRequestDirector is default implement.<br/>
	 * usually use in version 4.0-4.2<br/>
	 * since 4.3, this class is Deprecated.
	 */
	@Override
	public String enhanceClassName() {
		return "org.apache.http.impl.client.DefaultRequestDirector";
	}

	@Override
	protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
		return new InstanceMethodsInterceptPoint[]{new InstanceMethodsInterceptPoint() {
			@Override
			public MethodMatcher[] getMethodsMatchers() {
				return new MethodMatcher[] {
						new SimpleMethodMatcher("execute")};
			}

			@Override
			public String getMethodsInterceptor() {
				return getInstanceMethodsInterceptor();
			}
		}};
	}
}
