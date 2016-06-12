package com.ai.cloud.skywalking.jedis.v2.plugin;

import java.net.URI;

import redis.clients.jedis.JedisShardInfo;

import com.ai.cloud.skywalking.buriedpoint.RPCBuriedPointSender;
import com.ai.cloud.skywalking.model.Identification;
import com.ai.cloud.skywalking.model.Identification.IdentificationBuilder;
import com.ai.cloud.skywalking.plugin.interceptor.ConstructorInvokeContext;
import com.ai.cloud.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.ai.cloud.skywalking.plugin.interceptor.MethodInvokeContext;
import com.ai.cloud.skywalking.plugin.interceptor.assist.FirstInvokeInterceptor;

public class JedisInterceptor extends FirstInvokeInterceptor {
	private static final String REDIS_CONN_INFO_KEY = "redisConnInfo";

	private static RPCBuriedPointSender sender = new RPCBuriedPointSender();

	@Override
	public void onConstruct(EnhancedClassInstanceContext context,
			ConstructorInvokeContext interceptorContext) {
		String redisConnInfo = "";
		if (interceptorContext.allArguments().length > 0) {
			if (interceptorContext.allArguments()[0] instanceof String) {
				redisConnInfo = (String) interceptorContext.allArguments()[0];
				if (interceptorContext.allArguments().length > 1) {
					redisConnInfo += ":"
							+ (Integer) interceptorContext.allArguments()[1];
				}
			} else if (interceptorContext.allArguments()[0] instanceof JedisShardInfo) {
				JedisShardInfo shardInfo = (JedisShardInfo) interceptorContext
						.allArguments()[0];
				redisConnInfo = shardInfo.getHost() + ":" + shardInfo.getPort();
			} else if (interceptorContext.allArguments()[0] instanceof URI) {
				URI uri = (URI) interceptorContext.allArguments()[0];
				redisConnInfo = uri.getHost() + ":" + uri.getPort();
			}
		}
		context.set(REDIS_CONN_INFO_KEY, redisConnInfo);
	}

	@Override
	public void beforeMethod(EnhancedClassInstanceContext context,
			MethodInvokeContext interceptorContext) {
		if (this.isFirstBeforeMethod(context)) {
			/**
			 * redis server wouldn't process rpc context. ignore the
			 * return(ContextData) of sender's beforeSend
			 */
			IdentificationBuilder builder = Identification
					.newBuilder()
					.viewPoint(
							context.get(REDIS_CONN_INFO_KEY, String.class)
									+ " " + interceptorContext.methodName())
					.spanType(RedisBuriedPointType.instance());
			if (interceptorContext.allArguments().length > 0
					&& interceptorContext.allArguments()[0] instanceof String) {
				builder.businessKey("key="
						+ interceptorContext.allArguments()[0]);
			}
			sender.beforeSend(builder.build());
		}
	}

	@Override
	public Object afterMethod(EnhancedClassInstanceContext context,
			MethodInvokeContext interceptorContext, Object ret) {
		if (this.isLastAfterMethod(context)) {
			sender.afterSend();
		}
		return ret;
	}

	@Override
	public void handleMethodException(Throwable t,
			EnhancedClassInstanceContext context,
			MethodInvokeContext interceptorContext, Object ret) {
		sender.handleException(t);
	}

}
