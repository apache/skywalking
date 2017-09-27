package org.skywalking.apm.plugin.xmemcached.v2;

import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

public class XMemcachedConstructorWithHostPortArgInterceptor  implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        String host = (String)allArguments[0];
        String port = "11211";
        if (allArguments.length > 1) {
            port = String.valueOf(allArguments[1]);
        }
        objInst.setSkyWalkingDynamicField(host + ":" + port);
    }
}
