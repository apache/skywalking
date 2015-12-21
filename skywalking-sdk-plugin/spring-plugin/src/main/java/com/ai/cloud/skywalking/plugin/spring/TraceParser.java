package com.ai.cloud.skywalking.plugin.spring;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

public class TraceParser implements BeanDefinitionParser {

    private final String TRACE_APPLICATION_BEAN_NAME = "TracingApplication";

    @Override
    public BeanDefinition parse(Element element, ParserContext parserContext) {
        if (parserContext.getRegistry().containsBeanDefinition(TRACE_APPLICATION_BEAN_NAME)) {
            //  只能存在一个
            throw new IllegalStateException("Duplicate spring bean id ");
        }
        boolean turnOn = Boolean.parseBoolean(element.getAttribute("turnOn"));
        RootBeanDefinition rootBeanDefinition = new RootBeanDefinition();
        rootBeanDefinition.setLazyInit(false);
        rootBeanDefinition.setBeanClass(TracingEnhanceProcessor.class);
        rootBeanDefinition.getPropertyValues().addPropertyValue("turnOn", turnOn);
        parserContext.getRegistry().registerBeanDefinition(TRACE_APPLICATION_BEAN_NAME, rootBeanDefinition);
        return rootBeanDefinition;
    }
}
