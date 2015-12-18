package com.ai.cloud.skywalking.plugin.spring;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

public class TraceParser implements BeanDefinitionParser {
    @Override
    public BeanDefinition parse(Element element, ParserContext parserContext) {
        RootBeanDefinition rootBeanDefinition = new RootBeanDefinition();
        rootBeanDefinition.setLazyInit(false);
        rootBeanDefinition.setBeanClass(TracingEnhanceProcessor.class);
        String id = null;
        id = element.getAttribute("name");
        int counter = 2;
        while (parserContext.getRegistry().containsBeanDefinition(id)) {
            id = id + (counter++);
        }
        if (id != null && id.length() > 0) {
            if (parserContext.getRegistry().containsBeanDefinition(id)) {
                throw new IllegalStateException("Duplicate spring bean id " + id);
            }
            parserContext.getRegistry().registerBeanDefinition(id, rootBeanDefinition);
        }
        return rootBeanDefinition;
    }
}
