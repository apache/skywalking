package org.skywalking.apm.plugin.jedis.v2;

import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import redis.clients.jedis.HostAndPort;

public class JedisClusterConstructorWithHostAndPortArgInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        HostAndPort hostAndPort = (HostAndPort)allArguments[0];
        objInst.setSkyWalkingDynamicField(hostAndPort.getHost() + ":" + hostAndPort.getPort());
    }
}
