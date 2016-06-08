package org.skywalking.httpClient.v4.plugin.dubbox.rest.attachment;

import org.skywalking.httpClient.v4.plugin.HttpClientExecuteInterceptor;

import com.ai.cloud.skywalking.plugin.interceptor.IAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.InterceptorDefine;
import com.ai.cloud.skywalking.plugin.interceptor.MethodNameMatcher;

public class DubboxRestHeadSetterAttachment implements InterceptorDefine {

	/**
	 * this method is called as InterceptorDefine<br/>
	 * don't return be intercepted classname, <br/>
	 * just run as a pre setter of attribute:HttpClientExecuteInterceptor.TRACE_HEAD_NAME
	 */
	@Override
	public String getBeInterceptedClassName() {
		HttpClientExecuteInterceptor.TRACE_HEAD_NAME = "Dubbo-Attachments";
		return null;
	}

	@Override
	public MethodNameMatcher[] getBeInterceptedMethodsMatchers() {
		return null;
	}

	@Override
	public IAroundInterceptor instance() {
		return null;
	}

}
