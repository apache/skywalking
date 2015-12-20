package com.ai.cloud.skywalking.plugin.spring;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class TracingPatternParser implements BeanDefinitionParser {
    @Override
    public BeanDefinition parse(Element element, ParserContext parserContext) {
        // 获取Method并处理
        String methodPattern = element.getAttribute("method");
        if (methodPattern == null || methodPattern.length() == 0) {
            throw new IllegalStateException("Miss method pattern");
        }
        RootBeanDefinition beanDefinition = new RootBeanDefinition();
        beanDefinition.setBeanClass(TracingPattern.class);
        String id = element.getAttribute("id");
        if (id == null || id.length() == 0) {
            id = TracingPattern.class.getName();
            int counter = 2;
            while (parserContext.getRegistry().containsBeanDefinition(id)) {
                id = id + (counter++);
            }
        }
        if (id != null && id.length() > 0) {
            if (parserContext.getRegistry().containsBeanDefinition(id)) {
                throw new IllegalStateException("Duplicate spring bean id " + id);
            }
            parserContext.getRegistry().registerBeanDefinition(id, beanDefinition);
        }
        NamedNodeMap nnm = element.getAttributes();
        for (int i = 0; i < nnm.getLength(); i++) {
            Node node = nnm.item(i);
            String key = node.getLocalName();
            String value = node.getNodeValue();
            if (key.equals("entity")) {
                if (parserContext.getRegistry().containsBeanDefinition(value)) {
                    beanDefinition.getPropertyValues().add(key, parserContext.getRegistry().getBeanDefinition(value));
                } else {
                    beanDefinition.getPropertyValues().add(key, new RuntimeBeanReference(value));
                }
            } else {
                beanDefinition.getPropertyValues().add(key, value);
            }
        }

        return beanDefinition;
    }
}
