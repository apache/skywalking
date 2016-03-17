package org.skywalking.httpClient.v4.plugin;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;

import com.ai.cloud.skywalking.buriedpoint.RPCBuriedPointSender;
import com.ai.cloud.skywalking.model.Identification;
import com.ai.cloud.skywalking.plugin.interceptor.ConstructorInvokeContext;
import com.ai.cloud.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.ai.cloud.skywalking.plugin.interceptor.IAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.MethodInvokeContext;

public class HttpClientExecuteInterceptor implements IAroundInterceptor {
	/**
	 * default headname of sky walking context<br/>
	 */
	public static String TRACE_HEAD_NAME = "SkyWalking-TRACING-NAME";

	private static RPCBuriedPointSender sender = new RPCBuriedPointSender();

	@Override
	public void onConstruct(EnhancedClassInstanceContext context,
			ConstructorInvokeContext interceptorContext) {
	}

	@Override
	public void beforeMethod(EnhancedClassInstanceContext context,
			MethodInvokeContext interceptorContext) {
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
								+ sender.beforeSend(
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
			MethodInvokeContext interceptorContext, Object ret) {
		Object[] allArguments = interceptorContext.allArguments();
		if (allArguments[0] == null || allArguments[1] == null) {
			// illegal args, can't trace. ignore.
			return ret;
		}
		sender.afterSend();
		return ret;
	}

	@Override
	public void handleMethodException(Throwable t,
			EnhancedClassInstanceContext context,
			MethodInvokeContext interceptorContext, Object ret) {
		Object[] allArguments = interceptorContext.allArguments();
		if (allArguments[0] == null || allArguments[1] == null) {
			// illegal args, can't trace. ignore.
			return;
		}
		sender.handleException(t);
	}

}
