package org.skywalking.apm.plugin.spring.mvc;

import java.util.HashMap;
import java.util.Map;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.springframework.web.bind.annotation.RequestMapping;

public class ControllerConstructorInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        String basePath = "";
        RequestMapping basePathRequestMapping = objInst.getClass().getAnnotation(RequestMapping.class);
        if (basePathRequestMapping != null) {
            basePath = basePathRequestMapping.value()[0];
        }
        Map<Object, String> cacheRequestPath = new HashMap<Object, String>();
        cacheRequestPath.put("BASE_PATH", basePath);
        objInst.setSkyWalkingDynamicField(cacheRequestPath);
    }
}
