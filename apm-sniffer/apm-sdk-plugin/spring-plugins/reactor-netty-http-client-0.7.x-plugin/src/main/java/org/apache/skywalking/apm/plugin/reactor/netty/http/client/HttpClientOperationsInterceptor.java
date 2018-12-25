package org.apache.skywalking.apm.plugin.reactor.netty.http.client;

import io.netty.handler.codec.http.HttpRequest;
import java.lang.reflect.Field;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.*;

/**
 * @author jian.tan
 */
public class HttpClientOperationsInterceptor implements InstanceConstructorInterceptor {

    @Override public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        try {
            Field requestField = objInst.getClass().getDeclaredField("nettyRequest");
            requestField.setAccessible(true);
            HttpRequest httpRequest = (HttpRequest)requestField.get(objInst);
            //objInst.setSkyWalkingDynamicField(httpRequest);
            ContextManager.getRuntimeContext().put("SW_NETTY_HTTP_CLIENT_REQUEST", httpRequest);
            //ContextManager.getRuntimeContext().put(NETTY_REQUEST_KEY, httpRequest);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
