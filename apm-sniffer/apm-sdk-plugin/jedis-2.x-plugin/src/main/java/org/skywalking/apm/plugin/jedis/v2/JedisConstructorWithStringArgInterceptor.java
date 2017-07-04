package org.skywalking.apm.plugin.jedis.v2;

import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

public class JedisConstructorWithStringArgInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        String host = (String)allArguments[0];
        String port = "6379";
        if (allArguments.length > 1) {
            port = String.valueOf(allArguments[1]);
        }

        objInst.setSkyWalkingDynamicField(host + ":" + port);
    }
}
