package org.skywalking.apm.plugin.xmemcached.v2;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Map.Entry;

import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

public class XMemcachedConstructorWithComplexArgInterceptor    implements InstanceConstructorInterceptor {

	@Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
    	StringBuilder memcachConnInfo = new StringBuilder();
    	@SuppressWarnings("unchecked")
		Map<InetSocketAddress, InetSocketAddress> inetSocketAddressMap = (Map<InetSocketAddress, InetSocketAddress>)allArguments[0];
    	StringBuilder master = new StringBuilder();
    	for(Entry<InetSocketAddress, InetSocketAddress> entry : inetSocketAddressMap.entrySet()) {
    		if(master.length()<=0) {
    			InetSocketAddress masterInetSocketAddress = entry.getKey();
    			String host = masterInetSocketAddress.getAddress().getHostAddress();
            	int port = masterInetSocketAddress.getPort();
            	master.append(host+":"+port).append(";");
    		}
    		InetSocketAddress inetSocketAddress = entry.getValue();
    		String host = inetSocketAddress.getAddress().getHostAddress();
        	int port = inetSocketAddress.getPort();
        	memcachConnInfo.append(host+":"+port).append(";");
    	}
    	memcachConnInfo =  master.append(memcachConnInfo);
        objInst.setSkyWalkingDynamicField(memcachConnInfo.toString());
    }
}
