package com.a.eye.skywalking.api.plugin.httpClient.v4.define;

import com.a.eye.skywalking.api.plugin.interceptor.InstanceMethodsInterceptPoint;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

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
	protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
		return new InstanceMethodsInterceptPoint[]{new InstanceMethodsInterceptPoint() {
			@Override
			public ElementMatcher<MethodDescription> getMethodsMatcher() {
				return named("doExecute");
			}

			@Override
			public String getMethodsInterceptor() {
				return getInstanceMethodsInterceptor();
			}
		}};
	}
}
