package org.skywalking.apm.plugin.spymemcached.v2;

import java.net.InetSocketAddress;
import java.util.List;

import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogManager;

public class MemcachedConstructorWithInetSocketAddressListArgInterceptor implements InstanceConstructorInterceptor {
    
    private static final ILog logger = LogManager.getLogger(MemcachedConstructorWithInetSocketAddressListArgInterceptor.class);
    
    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        logger.info("MemcachedConstructorWithInetSocketAddressListArgInterceptor-----------------{}.", allArguments);
        StringBuilder memcachConnInfo = new StringBuilder();
        @SuppressWarnings("unchecked")
        List<InetSocketAddress> inetSocketAddressList = (List<InetSocketAddress>)allArguments[1];
        for (InetSocketAddress inetSocketAddress : inetSocketAddressList) {
            String host = inetSocketAddress.getAddress().getHostAddress();
            int port = inetSocketAddress.getPort();
            memcachConnInfo.append(host + ":" + port).append(";");
        }
        objInst.setSkyWalkingDynamicField(memcachConnInfo.toString());
    }
}