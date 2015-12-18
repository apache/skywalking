package com.ai.cloud.skywalking.plugin.spring.schema;

import com.ai.cloud.skywalking.plugin.spring.parser.TracingBeanDefinitionParser;
import com.ai.cloud.skywalking.plugin.spring.parser.TracingBeanPostProcessorParser;
import com.ai.cloud.skywalking.plugin.spring.parser.TracingPackageDefinitionParser;
import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

public class SWNamespaceHandler extends NamespaceHandlerSupport {

    @Override
    public void init() {
        registerBeanDefinitionParser("tracing-class", new TracingBeanDefinitionParser());
        registerBeanDefinitionParser("tracing-package", new TracingPackageDefinitionParser());
        registerBeanDefinitionParser("tracing", new TracingBeanPostProcessorParser());
    }
}
