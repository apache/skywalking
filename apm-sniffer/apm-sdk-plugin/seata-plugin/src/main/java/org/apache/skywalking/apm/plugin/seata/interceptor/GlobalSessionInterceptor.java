package org.apache.skywalking.apm.plugin.seata.interceptor;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

/**
 * @author kezhenxu94
 */
public class GlobalSessionInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(final EnhancedInstance objInst, final Object[] allArguments) {
    }
}
