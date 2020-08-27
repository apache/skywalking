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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.util.MethodUtil;
import org.apache.skywalking.apm.plugin.customize.constants.Constants;
import org.apache.skywalking.apm.plugin.customize.util.CustomizeUtil;
import org.apache.skywalking.apm.util.StringUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.apache.skywalking.apm.agent.core.plugin.bytebuddy.ArgumentTypeNameMatch.takesArgumentWithType;

/**
 * The CustomizeConfiguration class is the core class for parsing custom enhanced configuration files, parsing
 * configuration files, and converting content into plugins for loading into the kernel.
 */

public enum CustomizeConfiguration {

    INSTANCE;

    private static final ILog LOGGER = LogManager.getLogger(CustomizeConfiguration.class);

    /**
     * Some information after custom enhancements, this configuration is used by the custom enhancement plugin.
     * And using Map CONTEXT for avoiding classloader isolation issue.
     */
    private static final Map<String, Map<String, Object>> CONTEXT_METHOD_CONFIGURATIONS = new HashMap<>();
    private static final Map<String, ElementMatcher> CONTEXT_ENHANCE_CLASSES = new HashMap<>();
    private static final AtomicBoolean LOAD_FOR_CONFIGURATION = new AtomicBoolean(false);

    /**
     * The loadForEnhance method is resolver configuration file, and parse it
     */
    public void loadForEnhance() {
        try {
            for (Map<String, Object> configuration : resolver()) {
                addContextEnhanceClass(configuration);
            }
        } catch (Exception e) {
            LOGGER.error("CustomizeConfiguration loadForAgent fail", e);
        }
    }

    /**
     * The loadForConfiguration method is resolver configuration file, and parse it
     */
    public synchronized void loadForConfiguration() {
        if (LOAD_FOR_CONFIGURATION.get()) {
            return;
        }
        try {
            for (Map<String, Object> configuration : resolver()) {
                addContextMethodConfiguration(configuration);
            }
        } catch (Exception e) {
            LOGGER.error("CustomizeConfiguration loadForConfiguration fail", e);
        } finally {
            LOAD_FOR_CONFIGURATION.set(true);
        }
    }

    /**
     * Resolver custom enhancement file method total entry.
     *
     * @return configurations is a bridge resolver method and parse method, mainly used for decoupling.
     * @throws ParserConfigurationException link {@link ParserConfigurationException}
     * @throws IOException                  link {@link IOException}
     * @throws SAXException                 link {@link SAXException}
     */
    private List<Map<String, Object>> resolver() throws ParserConfigurationException, IOException, SAXException {
        List<Map<String, Object>> customizeMethods = new ArrayList<Map<String, Object>>();
        File file = new File(CustomizePluginConfig.Plugin.Customize.ENHANCE_FILE);
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
     * @throws IOException                  link {@link IOException}
     * @throws SAXException                 link {@link SAXException}
     */
    private NodeList resolverFileClassDesc(File file) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(file);
        return doc.getElementsByTagName(Constants.XML_ELEMENT_CLASS);
    }

    /**
     * Resolver all class description nodes to customizeMethods.
     *
     * @param classNodeList    all class description nodes.
     * @param customizeMethods of memory address, the element {@link MethodConfiguration}.
     */
    private void resolverClassNodeList(NodeList classNodeList, List<Map<String, Object>> customizeMethods) {
        for (int ec = 0; ec < classNodeList.getLength(); ec++) {
            Node classDesc = classNodeList.item(ec);
            NodeList methodNodeList = classDesc.getChildNodes();
            for (int ms = 0; ms < methodNodeList.getLength(); ms++) {
                Node methodDesc = methodNodeList.item(ms);
                if (methodDesc.getNodeType() == Node.ELEMENT_NODE) {
                    String className = classDesc.getAttributes()
                            .getNamedItem(Constants.XML_ELEMENT_CLASS_NAME)
                            .getNodeValue();
                    Map<String, Object> configuration = resolverMethodNodeDesc(className, methodDesc);
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
     * @return configurations is a bridge resolver method and parse method, mainly used for decoupling.
     */
    private Map<String, Object> resolverMethodNodeDesc(String className, Node methodDesc) {
        Map<String, Object> configuration = new HashMap<String, Object>();
        if (methodDesc.getAttributes().getNamedItem(Constants.XML_ELEMENT_OPERATION_NAME) != null) {
            MethodConfiguration.setOperationName(configuration, methodDesc.getAttributes()
                    .getNamedItem(
                            Constants.XML_ELEMENT_OPERATION_NAME)
                    .getNodeValue());
        }
        if (methodDesc.getAttributes().getNamedItem(Constants.XML_ELEMENT_CLOSE_BEFORE_METHOD) != null) {
            MethodConfiguration.setCloseBeforeMethod(configuration, Boolean.valueOf(methodDesc.getAttributes()
                    .getNamedItem(
                            Constants.XML_ELEMENT_CLOSE_BEFORE_METHOD)
                    .getNodeValue()));
        } else {
            MethodConfiguration.setCloseBeforeMethod(configuration, false);
        }
        if (methodDesc.getAttributes().getNamedItem(Constants.XML_ELEMENT_CLOSE_AFTER_METHOD) != null) {
            MethodConfiguration.setCloseAfterMethod(configuration, Boolean.valueOf(methodDesc.getAttributes()
                    .getNamedItem(
                            Constants.XML_ELEMENT_CLOSE_AFTER_METHOD)
                    .getNodeValue()));
        } else {
            MethodConfiguration.setCloseAfterMethod(configuration, false);
        }
        if (methodDesc.getAttributes().getNamedItem(Constants.XML_ELEMENT_METHOD_IS_STATIC) != null) {
            MethodConfiguration.setStatic(configuration, Boolean.valueOf(methodDesc.getAttributes()
                    .getNamedItem(
                            Constants.XML_ELEMENT_METHOD_IS_STATIC)
                    .getNodeValue()));
        }
        setAdvancedField(configuration, methodDesc);
        return resolverClassAndMethod(className, methodDesc.getAttributes()
                .getNamedItem(Constants.XML_ELEMENT_METHOD)
                .getNodeValue(), configuration);
    }

    /**
     * Add some private properties of the Advanced method configuration.
     *
     * @param configuration {@link MethodConfiguration}.
     * @param methodNode    method node.
     */
    private void setAdvancedField(Map<String, Object> configuration, Node methodNode) {
        NodeList methodContents = methodNode.getChildNodes();
        for (int mc = 0; mc < methodContents.getLength(); mc++) {
            Node methodContentNode = methodContents.item(mc);
            if (methodContentNode.getNodeType() == Node.ELEMENT_NODE) {
                if (Constants.XML_ELEMENT_OPERATION_NAME_SUFFIX.equals(methodContentNode.getNodeName())) {
                    MethodConfiguration.addOperationNameSuffixes(configuration, methodContentNode.getTextContent());
                }
                if (Constants.XML_ELEMENT_TAG.equals(methodContentNode.getNodeName())) {
                    MethodConfiguration.addTag(
                            configuration, methodContentNode.getAttributes()
                                    .getNamedItem(Constants.XML_ELEMENT_KEY)
                                    .getNodeValue(), methodContentNode.getTextContent());
                }
                if (Constants.XML_ELEMENT_LOG.equals(methodContentNode.getNodeName())) {
                    MethodConfiguration.addLog(
                            configuration, methodContentNode.getAttributes()
                                    .getNamedItem(Constants.XML_ELEMENT_KEY)
                                    .getNodeValue(), methodContentNode.getTextContent());
                }
            }
        }
    }

    /**
     * Parse class and method, if no error log is printed in this JVM, and return null. primitive desc impl by {@link
     * CustomizeUtil} At the bottom, the default operation name is added.
     *
     * @param className     class name.
     * @param methodDesc    method desc.
     * @param configuration {@link MethodConfiguration}.
     * @return configuration of method.
     */
    private Map<String, Object> resolverClassAndMethod(String className, String methodDesc,
                                                       Map<String, Object> configuration) {
        try {
            int openParen = methodDesc.indexOf(Constants.LEFT_PARENTHESIS);
            int closeParen = methodDesc.indexOf(Constants.RIGHT_PARENTHESIS);
            String methodName = methodDesc.substring(0, openParen);
            String[] arguments = methodDesc.substring(openParen + 1, closeParen).split(Constants.COMMA);
            MethodConfiguration.setClz(configuration, className);
            MethodConfiguration.setMethod(
                    configuration, CustomizeUtil.generateOperationName(className, methodName, arguments));
            MethodConfiguration.setMethodName(configuration, methodName);
            MethodConfiguration.setArguments(
                    configuration, StringUtil.isEmpty(arguments[0]) ? new String[0] : arguments);
            if (StringUtil.isEmpty(MethodConfiguration.getOperationName(configuration))) {
                MethodConfiguration.setOperationName(configuration, MethodConfiguration.getMethod(configuration));
            }
            return configuration;
        } catch (Exception e) {
            LOGGER.error(e, "Failed to resolver, className is {}, methodDesc is {}.", className, methodDesc);
        }
        return null;
    }

    /**
     * The configuration of each method is put into the kernel.
     *
     * @param configuration {@link MethodConfiguration}.
     */
    private void addContextMethodConfiguration(Map<String, Object> configuration) {
        getMethodConfigurations().put(MethodConfiguration.getMethod(configuration), configuration);
    }

    /**
     * The private method for get the configuration of this method.
     *
     * @return all method configs.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> getMethodConfigurations() {
        return CONTEXT_METHOD_CONFIGURATIONS;
    }

    /**
     * The configuration of each class is put into the kernel.
     *
     * @param configuration {@link MethodConfiguration}
     */
    private void addContextEnhanceClass(Map<String, Object> configuration) {
        String key = CustomizeUtil.generateClassDesc(
                MethodConfiguration.getClz(configuration), MethodConfiguration.isStatic(configuration));
        HashMap<String, ElementMatcher> enhanceClasses = getEnhanceClasses();
        ElementMatcher matcher = enhanceClasses.get(key);
        enhanceClasses.put(
                key, matcher == null ? parserMethodsMatcher(configuration) : ((ElementMatcher.Junction) matcher)
                        .or(parserMethodsMatcher(configuration)));
    }

    /**
     * Parse each configuration to matcher.
     *
     * @param configuration {@link MethodConfiguration}.
     * @return matcher {@link ElementMatcher}.
     */
    private ElementMatcher parserMethodsMatcher(Map<String, Object> configuration) {
        String[] arguments = MethodConfiguration.getArguments(configuration);
        ElementMatcher matcher = named(MethodConfiguration.getMethodName(configuration)).and(
                takesArguments(arguments.length));
        if (arguments.length > 0) {
            for (int i = 0; i < arguments.length; i++) {
                matcher = ((ElementMatcher.Junction) matcher).and(
                        CustomizeUtil.isJavaClass(arguments[i]) ? takesArgument(i, CustomizeUtil
                                .getJavaClass(arguments[i])) : takesArgumentWithType(i, arguments[i]));
            }
        }
        return matcher;
    }

    /**
     * Get InterceptPoints, the input dimension is class and is static.
     *
     * @param enhanceClass Real enhancement class
     * @param isStatic     Is it static, because static or not, logic is different in the SkyWalking kernel, so this
     *                     dimension is abstracted out.
     * @return all the interceptPoints.
     */
    public ElementMatcher getInterceptPoints(String enhanceClass, boolean isStatic) {
        HashMap<String, ElementMatcher> enhanceClasses = getEnhanceClasses();
        return enhanceClasses.get(CustomizeUtil.generateClassDesc(enhanceClass, isStatic));
    }

    /**
     * Get all the instrumentation {@link ClassEnhancePluginDefine} that need custom enhancements.
     *
     * @return all the custom instrumentation.
     */
    public Set<String> getInstrumentations() {
        HashMap<String, ElementMatcher> enhanceClasses = getEnhanceClasses();
        return enhanceClasses.keySet();
    }

    /**
     * Get all the private methods of interceptPoints that need custom enhancements.
     *
     * @return all config of the custom instrumentation.
     */
    @SuppressWarnings("unchecked")
    private HashMap<String, ElementMatcher> getEnhanceClasses() {
        return (HashMap<String, ElementMatcher>) CONTEXT_ENHANCE_CLASSES;
    }

    public Map<String, Object> getConfiguration(Method method) {
        if (!LOAD_FOR_CONFIGURATION.get()) {
            loadForConfiguration();
        }
        return getMethodConfigurations().get(MethodUtil.generateOperationName(method));
    }
}

