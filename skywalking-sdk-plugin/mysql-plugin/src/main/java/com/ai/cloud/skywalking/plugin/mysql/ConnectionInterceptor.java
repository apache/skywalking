package com.ai.cloud.skywalking.plugin.mysql;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Properties;

import com.ai.cloud.skywalking.buriedpoint.RPCBuriedPointSender;
import com.ai.cloud.skywalking.model.Identification;
import com.ai.cloud.skywalking.plugin.interceptor.ConstructorInvokeContext;
import com.ai.cloud.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.ai.cloud.skywalking.plugin.interceptor.IAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.MethodInvokeContext;

public class ConnectionInterceptor implements IAroundInterceptor {
	private static final String CONNECTION_INFO_KEY = "connectInfo";

	private static RPCBuriedPointSender sender = new RPCBuriedPointSender();

	@Override
	public void onConstruct(EnhancedClassInstanceContext context,
			ConstructorInvokeContext interceptorContext) {
		String url = (String) interceptorContext.allArguments()[4];
		Properties info = (Properties) interceptorContext.allArguments()[2];
		context.set(CONNECTION_INFO_KEY, url + "(" + info.getProperty("user")
				+ ")");
	}

	@Override
	public void beforeMethod(EnhancedClassInstanceContext context,
			MethodInvokeContext interceptorContext) {
		if(context == null){
			// be intercepted method could be called in constructor, ignore.
			return;
		}
		String connectInfo = context.get(CONNECTION_INFO_KEY, String.class);
		String method = interceptorContext.methodName();
		String sql = "";
		switch (method) {
		case "commit":
			this.beforeSend(connectInfo, method, sql);
			break;
		case "rollback":
			this.beforeSend(connectInfo, method, sql);
			break;
		case "close":
			this.beforeSend(connectInfo, method, sql);
			break;
		}
	}

	private void beforeSend(String connectInfo, String method, String sql) {
		sender.beforeSend(Identification
				.newBuilder()
				.viewPoint(connectInfo)
				.businessKey(
						"connection."
								+ method
								+ (sql == null || sql.length() == 0 ? "" : ":"
										+ sql))
				.spanType(JDBCBuriedPointType.instance()).build());
	}

	@Override
	public Object afterMethod(EnhancedClassInstanceContext context,
			MethodInvokeContext interceptorContext, Object ret) {
		if(context == null){
			// be intercepted method could be called in constructor, ignore.
			return ret;
		}
		String connectInfo = context.get(CONNECTION_INFO_KEY, String.class);
		switch (interceptorContext.methodName()) {
		case "createStatement":
			return new SWStatement((Connection) interceptorContext.inst(),
					(Statement) ret, connectInfo);
		case "prepareStatement":
			String sql = (String) interceptorContext.allArguments()[0];
			return new SWPreparedStatement(
					(Connection) interceptorContext.inst(),
					(PreparedStatement) ret, connectInfo, sql);
		case "prepareCall":
			String callableSql = (String) interceptorContext.allArguments()[0];
			return new SWCallableStatement(
					(Connection) interceptorContext.inst(),
					(CallableStatement) ret, connectInfo, callableSql);
		case "commit":
		case "rollback":
		case "close":
			sender.afterSend();
		}
		return ret;
	}

	@Override
	public void handleMethodException(Throwable t,
			EnhancedClassInstanceContext context,
			MethodInvokeContext interceptorContext, Object ret) {
		switch (interceptorContext.methodName()) {
		case "commit":
		case "rollback":
		case "close":
			sender.handleException(t);
			break;
		}
	}
}
