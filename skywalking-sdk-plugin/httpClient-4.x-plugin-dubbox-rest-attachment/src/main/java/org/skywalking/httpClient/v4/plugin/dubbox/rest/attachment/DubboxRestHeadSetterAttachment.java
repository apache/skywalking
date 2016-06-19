package org.skywalking.httpClient.v4.plugin.dubbox.rest.attachment;

import org.skywalking.httpClient.v4.plugin.HttpClientExecuteInterceptor;

import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.IntanceMethodsAroundInterceptor;

public class DubboxRestHeadSetterAttachment extends ClassInstanceMethodsEnhancePluginDefine {

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
	public MethodMatcher[] getInstanceMethodsMatchers() {
		return null;
	}

	@Override
	public IntanceMethodsAroundInterceptor getInstanceMethodsInterceptor() {
		return null;
	}

}
