package org.skywalking.httpClient.v4.plugin.define;

import com.ai.cloud.skywalking.plugin.interceptor.InterceptPoint;
import com.ai.cloud.skywalking.plugin.interceptor.MethodNameMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.FullNameMatcher;

public class DefaultRequestDirectorPluginDefine extends HttpClientPluginDefine {
	/**
	 * DefaultRequestDirector is default implement.<br/>
	 * usually use in version 4.0-4.2<br/>
	 * since 4.3, this class is Deprecated.
	 */
	@Override
	public String getBeInterceptedClassName() {
		return "org.apache.http.impl.client.DefaultRequestDirector";
	}

	@Override
	public MethodNameMatcher[] getBeInterceptedMethods() {
		return new MethodNameMatcher[] {
				new FullNameMatcher("execute")};
	}

}
