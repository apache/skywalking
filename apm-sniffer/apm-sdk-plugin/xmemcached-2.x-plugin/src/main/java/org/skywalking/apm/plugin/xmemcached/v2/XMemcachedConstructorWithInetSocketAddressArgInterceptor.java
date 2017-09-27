package org.skywalking.apm.plugin.xmemcached.v2;

import java.net.InetSocketAddress;

import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

public class XMemcachedConstructorWithInetSocketAddressArgInterceptor   implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
    	InetSocketAddress inetSocketAddress = (InetSocketAddress)allArguments[0];
    	String host = inetSocketAddress.getAddress().getHostAddress();
    	int port = inetSocketAddress.getPort();
        objInst.setSkyWalkingDynamicField(host + ":" + port);
    }
}

