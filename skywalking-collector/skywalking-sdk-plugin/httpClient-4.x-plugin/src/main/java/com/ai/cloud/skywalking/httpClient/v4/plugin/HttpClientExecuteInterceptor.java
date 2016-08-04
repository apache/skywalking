package com.ai.cloud.skywalking.httpClient.v4.plugin;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;

import com.ai.cloud.skywalking.invoke.monitor.RPCClientInvokeMonitor;
import com.ai.cloud.skywalking.model.Identification;
import com.ai.cloud.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.ConstructorInvokeContext;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.MethodInterceptResult;

public class HttpClientExecuteInterceptor implements InstanceMethodsAroundInterceptor {
	/**
	 * default headname of sky walking context<br/>
	 */
	public static String TRACE_HEAD_NAME = "SkyWalking-TRACING-NAME";

	private static RPCClientInvokeMonitor rpcClientInvokeMonitor = new RPCClientInvokeMonitor();

	@Override
	public void onConstruct(EnhancedClassInstanceContext context,
			ConstructorInvokeContext interceptorContext) {
	}

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
																.instance())
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
