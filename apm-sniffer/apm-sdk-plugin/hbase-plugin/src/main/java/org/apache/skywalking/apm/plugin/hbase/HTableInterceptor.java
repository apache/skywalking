package org.apache.skywalking.apm.plugin.hbase;

import java.lang.reflect.Method;
import java.util.List;

import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Mutation;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.StringTag;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * @author zhangbin
 * @email 675953827@qq.com
 * @date 2019/4/26 22:14
 */
public class HTableInterceptor implements InstanceMethodsAroundInterceptor {

    private static final ILog LOGGER = LogManager.getLogger(HTableInterceptor.class);

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        LOGGER.info("method is {} ", method.getName());
        AbstractSpan span = ContextManager.createExitSpan(HBasePluginConstants.HBASE_CLIENT_TABLE + "/" + method.getName(), "");
        span.setComponent(ComponentsDefine.HBASE);
        span.tag(new StringTag("args"), parseAttributes(allArguments));
        SpanLayer.asDB(span);

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {

    }

    protected String parseAttributes(Object[] args) {

        Object param = null;

        if (args != null && args.length == 1) { // only one
            param = args[0];
        } else if (args != null && args.length > 1) { // last param
            param = args[args.length - 1];
        } else {
            return null;
        }

        // Put/Delete/Append/Increment
        if (param instanceof Mutation) {
            Mutation mutation = (Mutation) param;
            return "rowKey: " + Bytes.toStringBinary(mutation.getRow());
        }
        if (param instanceof Get) {
            Get get = (Get) param;
            return "rowKey: " + Bytes.toStringBinary(get.getRow());
        }
        if (param instanceof Scan) {
            Scan scan = (Scan) param;
            String startRowKey = Bytes.toStringBinary(scan.getStartRow());
            String stopRowKey = Bytes.toStringBinary(scan.getStopRow());
            return "startRowKey: " + startRowKey + " stopRowKey: " + stopRowKey;
        }
        // if param instanceof List.
        if (param instanceof List) {
            List list = (List) param;
            return "size: " + list.size();
        }
        return null;
    }
}
