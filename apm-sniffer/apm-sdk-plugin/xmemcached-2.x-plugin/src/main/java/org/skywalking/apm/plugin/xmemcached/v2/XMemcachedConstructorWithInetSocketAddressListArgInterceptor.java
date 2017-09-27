package org.skywalking.apm.plugin.xmemcached.v2;

import java.net.InetSocketAddress;
import java.util.List;

import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

public class XMemcachedConstructorWithInetSocketAddressListArgInterceptor  implements InstanceConstructorInterceptor {

	@Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
    	StringBuilder memcachConnInfo = new StringBuilder();
    	@SuppressWarnings("unchecked")
    	List<InetSocketAddress> inetSocketAddressList = (List<InetSocketAddress>)allArguments[0];
    	for(InetSocketAddress inetSocketAddress : inetSocketAddressList) {
    		String host = inetSocketAddress.getAddress().getHostAddress();
        	int port = inetSocketAddress.getPort();
        	memcachConnInfo.append(host+":"+port).append(";");
    	}
        objInst.setSkyWalkingDynamicField(memcachConnInfo.toString());
    }
}
