package org.skywalking.apm.plugin.jedis.v2;

import java.net.URI;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

public class JedisConstructorWithUriArgInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        URI uri = (URI)allArguments[0];
        objInst.setSkyWalkingDynamicField(uri.getHost() + ":" + uri.getPort());
    }
}
