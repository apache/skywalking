package org.skywalking.apm.plugin.nutz.http.sync;

import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

public class SenderConstructorInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(final EnhancedInstance objInst, final Object[] allArguments) {
        objInst.setSkyWalkingDynamicField(allArguments[0]);
    }
}
