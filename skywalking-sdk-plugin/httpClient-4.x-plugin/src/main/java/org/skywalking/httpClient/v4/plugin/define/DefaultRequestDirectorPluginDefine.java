package org.skywalking.httpClient.v4.plugin.define;

import com.ai.cloud.skywalking.plugin.interceptor.InterceptPoint;

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
	public InterceptPoint[] getBeInterceptedMethods() {
		return new InterceptPoint[] {
				new InterceptPoint("execute")};
	}

}
