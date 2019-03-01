/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.plugin.customize.conf;

import javafx.util.Pair;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassEnhancePluginDefine;
import org.apache.skywalking.apm.plugin.customize.constants.Constant;
import org.apache.skywalking.apm.plugin.customize.constants.CustomizeLanguage;
import org.apache.skywalking.apm.util.ClassUtil;
import org.apache.skywalking.apm.util.MethodUtil;
import org.apache.skywalking.apm.util.StringUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * The CustomizeConfiguration class is the core class for
 * parsing custom enhanced configuration files,
 * parsing configuration files,
 * and converting content into plugins for loading into the kernel.
 *
 * @author zhaoyuguang
 */

public enum CustomizeConfiguration {

    INSTANCE;

    private static final ILog logger = LogManager.getLogger(CustomizeConfiguration.class);

    /**
     * The load method is resolver configuration file,
     * and parser it to kernel.
     */
    public void load() {
        try {
            parser(resolver());
        } catch (Exception e) {
            logger.error("CustomizeConfiguration load fail", e);
        }
    }

    /**
     * Resolver custom enhancement file method total entry.
     *
     * @return configurations is a bridge resolver method and parser method,
     * mainly used for decoupling.
     * @throws ParserConfigurationException link {@link ParserConfigurationException}
     * @throws IOException link {@link IOException}
     * @throws SAXException link {@link SAXException}
     */
    private List<DefaultMethodConfiguration> resolver() throws ParserConfigurationException, IOException, SAXException {
        List<DefaultMethodConfiguration> customizeMethods = new ArrayList<DefaultMethodConfiguration>();
        File file = new File(Config.Customize.ENHANCE_FILE);
        if (file.exists() && file.isFile()) {
            NodeList classNodeList = resolverFileClassDesc(file);
            resolverClassNodeList(classNodeList, customizeMethods);
        }
        return customizeMethods;
    }

    /**
     * According to the custom enhancement file, return lass description nodes in the file.
     *
     * @param file the custom enhanced files
     * @return all class description nodes
     * @throws ParserConfigurationException link {@link ParserConfigurationException}
     * @throws IOException link {@link IOException}
     * @throws SAXException link {@link SAXException}
     */
    private NodeList resolverFileClassDesc(File file) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(file);
        return doc.getElementsByTagName(Constant.XML_ELEMENT_CLASS);
    }

    /**
     * Resolver all class description nodes to customizeMethods.
     *
     * @param classNodeList    all class description nodes.
     * @param customizeMethods of memory address, the element {@link DefaultMethodConfiguration}.
     */
    private void resolverClassNodeList(NodeList classNodeList, List<DefaultMethodConfiguration> customizeMethods) {
        for (int ec = 0; ec < classNodeList.getLength(); ec++) {
            Node classDesc = classNodeList.item(ec);
            NodeList methodNodeList = classDesc.getChildNodes();
            for (int ms = 0; ms < methodNodeList.getLength(); ms++) {
                Node methodDesc = methodNodeList.item(ms);
                if (methodDesc.getNodeType() == Node.ELEMENT_NODE) {
                    String className = classDesc.getAttributes().getNamedItem(Constant.XML_ELEMENT_CLASS_NAME).getNodeValue();
                    DefaultMethodConfiguration configuration = resolverMethodNodeDesc(className, methodDesc);
                    if (configuration != null) {
                        customizeMethods.add(configuration);
                    }
                }
            }
        }
    }

    /**
     * Resolver according to the description of the method under the current class node.
     *
     * @param className  class name.
     * @param methodDesc method node.
     * @return configurations is a bridge resolver method and parser method,
     * mainly used for decoupling.
     */
    private DefaultMethodConfiguration resolverMethodNodeDesc(String className, Node methodDesc) {
        Node language = methodDesc.getAttributes().getNamedItem(Constant.XML_ELEMENT_LANGUAGE);
        DefaultMethodConfiguration configuration = new DefaultMethodConfiguration();
        if (language != null && CustomizeLanguage.SpEL.name().equals(language.getNodeValue())) {
            configuration = new SpELMethodConfiguration();
            configuration.setLanguage(CustomizeLanguage.SpEL);
            setSpELField((SpELMethodConfiguration) configuration, methodDesc);
        } else {
            configuration.setLanguage(CustomizeLanguage.DEFAULT);
        }
        if (methodDesc.getAttributes().getNamedItem(Constant.XML_ELEMENT_OPERATION_NAME) != null) {
            configuration.setOperationName(methodDesc.getAttributes().getNamedItem(Constant.XML_ELEMENT_OPERATION_NAME).getNodeValue());
        }
        if (methodDesc.getAttributes().getNamedItem(Constant.XML_ELEMENT_CLOSE_BEFORE_METHOD) != null) {
            configuration.setCloseBeforeMethod(Boolean.valueOf(methodDesc.getAttributes().getNamedItem(Constant.XML_ELEMENT_CLOSE_BEFORE_METHOD).getNodeValue()));
        }
        if (methodDesc.getAttributes().getNamedItem(Constant.XML_ELEMENT_CLOSE_AFTER_METHOD) != null) {
            configuration.setCloseAfterMethod(Boolean.valueOf(methodDesc.getAttributes().getNamedItem(Constant.XML_ELEMENT_CLOSE_AFTER_METHOD).getNodeValue()));
        }
        return resolverClassAndMethod(className,
                methodDesc.getAttributes().getNamedItem(Constant.XML_ELEMENT_METHOD).getNodeValue(),
                configuration);
    }

    /**
     * Add some private properties of the spel method configuration.
     *
     * @param configuration {@link SpELMethodConfiguration}.
     * @param methodNode    method node.
     */
    private void setSpELField(SpELMethodConfiguration configuration, Node methodNode) {
        NodeList methodContents = methodNode.getChildNodes();
        for (int mc = 0; mc < methodContents.getLength(); mc++) {
            Node methodContentNode = methodContents.item(mc);
            if (methodContentNode.getNodeType() == Node.ELEMENT_NODE) {
                if (Constant.XML_ELEMENT_OPERATION_NAME_SUFFIX.equals(methodContentNode.getNodeName())) {
                    configuration.getOperationNameSuffixes().add(methodContentNode.getTextContent());
                }
                if (Constant.XML_ELEMENT_TAG.equals(methodContentNode.getNodeName())) {
                    configuration.getTags().put(methodContentNode.getAttributes().getNamedItem(Constant.XML_ELEMENT_KEY).getNodeValue(), methodContentNode.getTextContent());
                }
                if (Constant.XML_ELEMENT_LOG.equals(methodContentNode.getNodeName())) {
                    configuration.getLogs().put(methodContentNode.getAttributes().getNamedItem(Constant.XML_ELEMENT_KEY).getNodeValue(), methodContentNode.getTextContent());
                }
            }
        }
    }

    /**
     * Parse class and method,
     * if no error log is printed in this JVM, and return null.
     * primitive desc impl by {@link ClassUtil}
     * At the bottom, the default operation name is added.
     *
     * @param className     class name.
     * @param methodDesc    method desc.
     * @param configuration {@link DefaultMethodConfiguration}.
     * @return configuration of method.
     */
    @SuppressWarnings("unchecked")
    private DefaultMethodConfiguration resolverClassAndMethod(String className, String methodDesc, DefaultMethodConfiguration configuration) {
        try {
            Class clz = Class.forName(className);
            int openParen = methodDesc.indexOf(Constant.LEFT_PARENTHESIS);
            int closeParen = methodDesc.indexOf(Constant.RIGHT_PARENTHESIS);
            String methodName = methodDesc.substring(0, openParen);
            Class[] parameterTypes = null;
            String[] arguments = methodDesc.substring(openParen + 1, closeParen).split(Constant.COMMA);
            if (arguments.length > 0) {
                parameterTypes = new Class[arguments.length];
                for (int i = 0; i < arguments.length; i++) {
                    parameterTypes[i] = ClassUtil.forName(arguments[i].trim());
                }
            }
            Method method = clz.getDeclaredMethod(methodName, parameterTypes);
            configuration.setClz(clz);
            configuration.setMethod(method);
            if(StringUtil.isEmpty(configuration.getOperationName())){
                configuration.setOperationName(MethodUtil.generateOperationName(method));
            }
            return configuration;
        } catch (Exception e) {
            logger.error(e, "Failed to resolver, className is {}, methodDesc is {}.", className, methodDesc);
        }
        return null;
    }

    /**
     * Put the plugin configuration into the kernel according to the configuration.
     *
     * @param configurations is a bridge resolver method and parser method,
     *                       mainly used for decoupling.
     */
    private void parser(List<DefaultMethodConfiguration> configurations) {
        init();
        for (DefaultMethodConfiguration configuration : configurations) {
            addContextMethodConfiguration(configuration);
            addContextEnhanceClass(configuration);
        }
    }

    /**
     * In order to avoid the judgment of the useless null pointer exception.
     */
    private void init() {
        Config.Customize.CONTEXT.put(Constant.CONTEXT_METHOD_CONFIGURATIONS, new HashMap<Method, DefaultMethodConfiguration>());
        Config.Customize.CONTEXT.put(Constant.CONTEXT_ENHANCE_CLASSES, new HashMap<Pair<Class, Boolean>, Map<CustomizeLanguage, ElementMatcher>>());
    }

    /**
     * The configuration of each method is put into the kernel.
     *
     * @param configuration {@link DefaultMethodConfiguration}.
     */
    private void addContextMethodConfiguration(DefaultMethodConfiguration configuration) {
        getMethodConfigurations().put(configuration.getMethod(), configuration);
    }


    /**
     * The private method for get the configuration of this method.
     * @return all method configs.
     */
    @SuppressWarnings("unchecked")
    private Map<Method, DefaultMethodConfiguration> getMethodConfigurations(){
        return ((Map<Method, DefaultMethodConfiguration>) Config.Customize.CONTEXT.get(Constant.CONTEXT_METHOD_CONFIGURATIONS));
    }

    /**
     * The configuration of each class is put into the kernel.
     *
     * @param configuration {@link DefaultMethodConfiguration}
     */
    private void addContextEnhanceClass(DefaultMethodConfiguration configuration) {
        Pair<Class, Boolean> key = new Pair<Class, Boolean>(configuration.getClz(), Modifier.isStatic(configuration.getMethod().getModifiers()));
        HashMap<Pair<Class, Boolean>, Map<CustomizeLanguage, ElementMatcher>> enhanceClasses = getEnhanceClasses();
        Map<CustomizeLanguage, ElementMatcher> value = enhanceClasses.get(key);
        ElementMatcher matcher;
        if (value == null) {
            matcher = parserMethodsMatcher(configuration);
            value = new HashMap<CustomizeLanguage, ElementMatcher>();
            value.put(configuration.getLanguage(), matcher);
            enhanceClasses.put(key, value);
        } else {
            matcher = ((ElementMatcher.Junction) value.get(configuration.getLanguage())).or(parserMethodsMatcher(configuration));
            value.put(configuration.getLanguage(), matcher);
        }
    }

    /**
     * Parse each configuration to matcher.
     *
     * @param configuration {@link DefaultMethodConfiguration}.
     * @return matcher {@link ElementMatcher}.
     */
    private ElementMatcher parserMethodsMatcher(DefaultMethodConfiguration configuration) {
        ElementMatcher matcher = named(configuration.getMethod().getName()).and(takesArguments(configuration.getMethod().getParameterTypes().length));
        if (configuration.getMethod().getParameterTypes().length > 0) {
            for (int i = 0; i < configuration.getMethod().getParameterTypes().length; i++) {
                matcher = ((ElementMatcher.Junction) matcher).and(takesArgument(i, configuration.getMethod().getParameterTypes()[i]));
            }
        }
        return matcher;
    }

    /**
     * Get InterceptPoints, the input dimension is class and is static.
     *
     * @param enhanceClass Real enhancement class
     * @param isStatic     Is it static, because static or not,
     *                     logic is different in the SkyWalking kernel,
     *                     so this dimension is abstracted out.
     * @return all the interceptPoints.
     */
    public Map<CustomizeLanguage, ElementMatcher> getInterceptPoints(Class enhanceClass, boolean isStatic) {
        HashMap<Pair<Class, Boolean>, Map<CustomizeLanguage, ElementMatcher>> enhanceClasses = getEnhanceClasses();
        return enhanceClasses.get(new Pair<Class, Boolean>(enhanceClass, isStatic));
    }

    /**
     * Get all the instrumentation {@link ClassEnhancePluginDefine} that need custom enhancements.
     *
     * @return all the custom instrumentation.
     */
    public Set<Pair<Class, Boolean>> getInstrumentations() {
        HashMap<Pair<Class, Boolean>, Map<CustomizeLanguage, ElementMatcher>> enhanceClasses = getEnhanceClasses();
        return enhanceClasses.keySet();
    }

    /**
     * Get all the private methods of interceptPoints that need custom enhancements.
     *
     * @return all config of the custom instrumentation.
     */
    @SuppressWarnings("unchecked")
    private HashMap<Pair<Class, Boolean>, Map<CustomizeLanguage, ElementMatcher>> getEnhanceClasses() {
        return (HashMap<Pair<Class, Boolean>, Map<CustomizeLanguage, ElementMatcher>>) Config.Customize.CONTEXT.get(Constant.CONTEXT_ENHANCE_CLASSES);
    }

    /**
     * Get the configuration of this method.
     *
     * @param method interceptor method.
     * @return configuration {@link DefaultMethodConfiguration}.
     */
    public DefaultMethodConfiguration getConfiguration(Method method) {
        return  getMethodConfigurations().get(method);
    }
}

