package org.skywalking.apm.plugin.spring.mvc;

import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The <code>ControllerConstructorInterceptor</code> intercepts the Controller's constructor, in order to acquire the
 * mapping annotation, if exist.
 *
 * But, you can see we only use the first mapping value, <B>Why?</B>
 *
 * Right now, we intercept the controller by annotation as you known, so we CAN'T know which uri patten is actually
 * matched. Even we know, that costs a lot.
 *
 * If we want to resolve that, we must intercept the Spring MVC core codes, that is not a good choice for now.
 *
 * Comment by @wu-sheng
 */
public class ControllerConstructorInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        String basePath = "";
        RequestMapping basePathRequestMapping = objInst.getClass().getAnnotation(RequestMapping.class);
        if (basePathRequestMapping != null) {
            if (basePathRequestMapping.value().length > 0) {
                basePath = basePathRequestMapping.value()[0];
            } else if (basePathRequestMapping.path().length > 0) {
                basePath = basePathRequestMapping.path()[0];
            }
        }
        PathMappingCache pathMappingCache = new PathMappingCache(basePath);
        objInst.setSkyWalkingDynamicField(pathMappingCache);
    }
}
