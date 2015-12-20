package com.ai.cloud.skywalking.plugin.spring;

import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

public class SpringTagNamespaceHandler extends NamespaceHandlerSupport {
    @Override
    public void init() {
        registerBeanDefinitionParser("tracing-bean", new TracingPatternParser());
        registerBeanDefinitionParser("trace", new TraceParser());
    }
}
