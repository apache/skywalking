package org.apache.skywalking.apm.plugin.jdbc;

import java.beans.Expression;
import java.lang.reflect.Method;
import java.sql.Connection;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.AbstractInstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodAroundContext;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.jdbc.connectionurl.parser.URLParser;
import org.apache.skywalking.apm.plugin.jdbc.trace.ConnectionInfo;

public class DataSourceInterceptor extends AbstractInstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
            Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
            Class<?>[] argumentsTypes, Object ret, MethodAroundContext context) throws Throwable {
        ConnectionInfo info = (ConnectionInfo)objInst.getSkyWalkingDynamicField();
        if (info == null) { 
            final Expression expression = new Expression(objInst,"getUrl",new Object[0]);
            final String url = (String)expression.getValue();
            info = URLParser.parser(url);
            objInst.setSkyWalkingDynamicField(info);
        }
        final Connection conn = (Connection)ret;
        return  new SWConnection(info,conn);
    }
}