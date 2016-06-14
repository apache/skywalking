package org.skywalking.httpClient.v4.plugin.dubbox.rest.attachment;

import org.skywalking.httpClient.v4.plugin.HttpClientExecuteInterceptor;

import com.ai.cloud.skywalking.plugin.interceptor.IAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.InterceptorPluginDefine;
import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;

public class DubboxRestHeadSetterAttachment extends InterceptorPluginDefine {

	/**
	 * this method is called as InterceptorPluginDefine<br/>
	 * don't return be intercepted classname, <br/>
	 * just run as a pre setter of attribute:HttpClientExecuteInterceptor.TRACE_HEAD_NAME
	 */
	@Override
	public String getBeInterceptedClassName() {
		HttpClientExecuteInterceptor.TRACE_HEAD_NAME = "Dubbo-Attachments";
		return null;
	}

	@Override
	public MethodMatcher[] getBeInterceptedMethodsMatchers() {
		return null;
	}

	@Override
	public IAroundInterceptor instance() {
		return null;
	}

}
