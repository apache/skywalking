package org.skywalking.apm.plugin.spring.mvc;

import java.lang.reflect.Method;

import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The <code>RequestMappingMethodInterceptor</code> only use the first mapping value.
 * it will inteceptor with <code>@RequestMapping</code>
 * @author clevertension
 */
public class RequestMappingMethodInterceptor extends AbstractMethodInteceptor {
    @Override
    public String getRequestURL(Method method) {
        String requestURL = "";
        RequestMapping methodRequestMapping = method.getAnnotation(RequestMapping.class);
        if (methodRequestMapping.value().length > 0) {
            requestURL = methodRequestMapping.value()[0];
        } else if (methodRequestMapping.path().length > 0) {
            requestURL = methodRequestMapping.path()[0];
        }
        return requestURL;
    }
}
