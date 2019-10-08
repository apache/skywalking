package com.apache.skywalking.apm.plugin.ehcache.v2;

import net.sf.ehcache.config.CacheConfiguration;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

/**
 * @Author MrPro
 */
public class EhcacheConstructorInterceptor implements InstanceConstructorInterceptor {
    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        CacheConfiguration cacheConfiguration = (CacheConfiguration) allArguments[0];

        // get cache name
        if (cacheConfiguration != null) {
            objInst.setSkyWalkingDynamicField(new EhcacheEnhanceInfo(cacheConfiguration.getName()));
        }
    }
}
