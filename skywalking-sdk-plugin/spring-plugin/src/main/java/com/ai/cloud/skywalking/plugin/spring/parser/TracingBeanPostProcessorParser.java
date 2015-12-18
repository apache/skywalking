package com.ai.cloud.skywalking.plugin.spring.parser;

import com.ai.cloud.skywalking.plugin.spring.TracingEnhanceProcessor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

public class TracingBeanPostProcessorParser implements BeanDefinitionParser {
    @Override
    public BeanDefinition parse(Element element, ParserContext parserContext) {
        RootBeanDefinition beanDefinition = new RootBeanDefinition();
        //
        beanDefinition.setBeanClass(TracingEnhanceProcessor.class);
        return beanDefinition;
    }
}
