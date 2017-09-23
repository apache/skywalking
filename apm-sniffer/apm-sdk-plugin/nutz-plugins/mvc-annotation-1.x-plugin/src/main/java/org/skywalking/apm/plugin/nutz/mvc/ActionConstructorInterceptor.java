package org.skywalking.apm.plugin.nutz.mvc;

import org.nutz.mvc.annotation.At;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

/**
 *
 * @author wendal
 */
public class ActionConstructorInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        String basePath = "";
        At basePathRequestMapping = objInst.getClass().getAnnotation(At.class);
        if (basePathRequestMapping != null) {
            basePath = basePathRequestMapping.value()[0];
        }
        PathMappingCache pathMappingCache = new PathMappingCache(basePath);
        objInst.setSkyWalkingDynamicField(pathMappingCache);
    }
}
