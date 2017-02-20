package com.a.eye.skywalking.api.plugin.httpClient.v4;

import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.ConstructorInvokeContext;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;

import com.a.eye.skywalking.invoke.monitor.RPCClientInvokeMonitor;
import com.a.eye.skywalking.model.Identification;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInterceptResult;

public class HttpClientExecuteInterceptor implements InstanceMethodsAroundInterceptor {
	/**
	 * default headname of sky walking context<br/>
	 */
	public static String TRACE_HEAD_NAME = "SkyWalking-TRACING-NAME";

	private static RPCClientInvokeMonitor rpcClientInvokeMonitor = new RPCClientInvokeMonitor();

	@Override
	public void beforeMethod(EnhancedClassInstanceContext context,
			InstanceMethodInvokeContext interceptorContext, MethodInterceptResult result) {
		Object[] allArguments = interceptorContext.allArguments();
		if (allArguments[0] == null || allArguments[1] == null) {
			// illegal args, can't trace. ignore.
			return;
		}
		HttpHost httpHost = (HttpHost) allArguments[0];
		HttpRequest httpRequest = (HttpRequest) allArguments[1];
		httpRequest
				.setHeader(
						TRACE_HEAD_NAME,
						"ContextData="
								+ rpcClientInvokeMonitor.beforeInvoke(
										Identification
												.newBuilder()
												.viewPoint(
														httpHost.toURI()
																.toString())
												.spanType(
														WebBuriedPointType
																.INSTANCE)
												.build()).toString());
	}

	@Override
	public Object afterMethod(EnhancedClassInstanceContext context,
			InstanceMethodInvokeContext interceptorContext, Object ret) {
		Object[] allArguments = interceptorContext.allArguments();
		if (allArguments[0] == null || allArguments[1] == null) {
			// illegal args, can't trace. ignore.
			return ret;
		}
		rpcClientInvokeMonitor.afterInvoke();
		return ret;
	}

	@Override
	public void handleMethodException(Throwable t, EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext) {
		Object[] allArguments = interceptorContext.allArguments();
		if (allArguments[0] == null || allArguments[1] == null) {
			// illegal args, can't trace. ignore.
			return;
		}
		rpcClientInvokeMonitor.occurException(t);
	}

}
