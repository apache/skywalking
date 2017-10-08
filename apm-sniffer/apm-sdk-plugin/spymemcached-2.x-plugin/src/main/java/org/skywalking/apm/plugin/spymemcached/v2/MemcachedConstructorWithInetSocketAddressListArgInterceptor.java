package org.skywalking.apm.plugin.spymemcached.v2;

import java.net.InetSocketAddress;
import java.util.List;

import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

public class MemcachedConstructorWithInetSocketAddressListArgInterceptor implements InstanceConstructorInterceptor {
    
    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        StringBuilder memcachConnInfo = new StringBuilder();
        @SuppressWarnings("unchecked")
        List<InetSocketAddress> inetSocketAddressList = (List<InetSocketAddress>)allArguments[1];
        for (InetSocketAddress inetSocketAddress : inetSocketAddressList) {
            String host = inetSocketAddress.getAddress().getHostAddress();
            int port = inetSocketAddress.getPort();
            memcachConnInfo.append(host).append(":").append(port).append(";");
        }
        if (memcachConnInfo.length() > 1) {
            memcachConnInfo = new StringBuilder(memcachConnInfo.substring(0, memcachConnInfo.length() - 1));
        }
        objInst.setSkyWalkingDynamicField(memcachConnInfo.toString());
    }
}