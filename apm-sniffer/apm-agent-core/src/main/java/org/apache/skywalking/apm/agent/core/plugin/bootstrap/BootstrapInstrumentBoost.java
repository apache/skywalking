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

package org.apache.skywalking.apm.agent.core.plugin.bootstrap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.pool.TypePool;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.AbstractClassEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.InstrumentDebuggingClass;
import org.apache.skywalking.apm.agent.core.plugin.PluginException;
import org.apache.skywalking.apm.agent.core.plugin.PluginFinder;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.StaticMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.loader.AgentClassLoader;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * If there is Bootstrap instrumentation plugin declared in plugin list, BootstrapInstrumentBoost inject the necessary
 * classes into bootstrap class loader, including generated dynamic delegate classes.
 *
 * @author wusheng
 */
public class BootstrapInstrumentBoost {
    private static final ILog logger = LogManager.getLogger(BootstrapInstrumentBoost.class);
    private static final String SHADE_PACKAGE = "org.apache.skywalking.apm.dependencies.";
    private static final String[] HIGH_PRIORITY_CLASSES = {
        "org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance",
        "org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.BootstrapInterRuntimeAssist",
        "org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor",
        "org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor",
        "org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.StaticMethodsAroundInterceptor",
        "org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult",
        "org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.OverrideCallable",
        "org.apache.skywalking.apm.agent.core.plugin.bootstrap.IBootstrapLog",
        SHADE_PACKAGE + "net.bytebuddy.implementation.bind.annotation.RuntimeType",
        SHADE_PACKAGE + "net.bytebuddy.implementation.bind.annotation.This",
        SHADE_PACKAGE + "net.bytebuddy.implementation.bind.annotation.AllArguments",
        SHADE_PACKAGE + "net.bytebuddy.implementation.bind.annotation.AllArguments$Assignment",
        SHADE_PACKAGE + "net.bytebuddy.implementation.bind.annotation.SuperCall",
        SHADE_PACKAGE + "net.bytebuddy.implementation.bind.annotation.Origin",
        SHADE_PACKAGE + "net.bytebuddy.implementation.bind.annotation.Morph"
    };
    private static String INSTANCE_METHOD_DELEGATE_TEMPLATE = "org.apache.skywalking.apm.agent.core.plugin.bootstrap.template.InstanceMethodInterTemplate";
    private static String INSTANCE_METHOD_WITH_OVERRIDE_ARGS_DELEGATE_TEMPLATE = "org.apache.skywalking.apm.agent.core.plugin.bootstrap.template.InstanceMethodInterWithOverrideArgsTemplate";
    private static String CONSTRUCTOR_DELEGATE_TEMPLATE = "org.apache.skywalking.apm.agent.core.plugin.bootstrap.template.ConstructorInterTemplate";
    private static String STATIC_METHOD_DELEGATE_TEMPLATE = "org.apache.skywalking.apm.agent.core.plugin.bootstrap.template.StaticMethodInterTemplate";
    private static String STATIC_METHOD_WITH_OVERRIDE_ARGS_DELEGATE_TEMPLATE = "org.apache.skywalking.apm.agent.core.plugin.bootstrap.template.StaticMethodInterWithOverrideArgsTemplate";

    public static AgentBuilder inject(PluginFinder pluginFinder, AgentBuilder agentBuilder,
        Instrumentation instrumentation) throws PluginException {
        Map<String, byte[]> classesTypeMap = new HashMap<String, byte[]>();

        if (!prepareJREInstrumentation(pluginFinder, classesTypeMap)) {
            return agentBuilder;
        }

        for (String highPriorityClass : HIGH_PRIORITY_CLASSES) {
            loadHighPriorityClass(classesTypeMap, highPriorityClass);
        }

        /**
         * This strategy is no longer available after Java 11.
         * Inject the classes into bootstrap class loader.
         */
        ClassInjector.UsingUnsafe.ofBootLoader().injectRaw(classesTypeMap);
        agentBuilder = agentBuilder.enableUnsafeBootstrapInjection();

        /**
         * Assures that all modules of the supplied types are read by the module of any instrumented type.
         * JDK Module system was introduced since JDK9.
         *
         * The following codes work only JDK Module system exist.
         */
        for (String highPriorityClass : HIGH_PRIORITY_CLASSES) {
            try {
                agentBuilder = agentBuilder.assureReadEdgeTo(instrumentation, Class.forName(highPriorityClass));
            } catch (ClassNotFoundException e) {
                logger.error(e, "Fail to open the high priority class " + highPriorityClass + " to public access in JDK9+");
                throw new UnsupportedOperationException("Fail to open the high priority class " + highPriorityClass + " to public access in JDK9+", e);
            }
        }
        for (String generatedClass : classesTypeMap.keySet()) {
            try {
                agentBuilder = agentBuilder.assureReadEdgeTo(instrumentation, Class.forName(generatedClass));
            } catch (ClassNotFoundException e) {
                logger.error(e, "Fail to open the high generated class " + generatedClass + " to public access in JDK9+");
                throw new UnsupportedOperationException("Fail to open the high generated class " + generatedClass + " to public access in JDK9+", e);
            }
        }

        return agentBuilder;
    }

    /**
     * Get the delegate class name.
     *
     * @param methodsInterceptor of original interceptor in the plugin
     * @return generated delegate class name
     */
    public static String internalDelegate(String methodsInterceptor) {
        return methodsInterceptor + "_internal";
    }

    /**
     * Load the delegate class from current class loader, mostly should be AppClassLoader.
     *
     * @param methodsInterceptor of original interceptor in the plugin
     * @return generated delegate class
     */
    public static Class forInternalDelegateClass(String methodsInterceptor) {
        try {
            return Class.forName(internalDelegate(methodsInterceptor));
        } catch (ClassNotFoundException e) {
            throw new PluginException(e.getMessage(), e);
        }
    }

    /**
     * Generate dynamic delegate for ByteBuddy
     *
     * @param pluginFinder gets the whole plugin list.
     * @param classesTypeMap hosts the class binary.
     * @return true if have JRE instrumentation requirement.
     * @throws PluginException when generate failure.
     */
    private static boolean prepareJREInstrumentation(PluginFinder pluginFinder,
        Map<String, byte[]> classesTypeMap) throws PluginException {
        TypePool typePool = TypePool.Default.of(BootstrapInstrumentBoost.class.getClassLoader());
        List<AbstractClassEnhancePluginDefine> bootstrapClassMatchDefines = pluginFinder.getBootstrapClassMatchDefine();
        for (AbstractClassEnhancePluginDefine define : bootstrapClassMatchDefines) {
            for (InstanceMethodsInterceptPoint point : define.getInstanceMethodsInterceptPoints()) {
                if (point.isOverrideArgs()) {
                    generateDelegator(classesTypeMap, typePool, INSTANCE_METHOD_WITH_OVERRIDE_ARGS_DELEGATE_TEMPLATE, point.getMethodsInterceptor());
                } else {
                    generateDelegator(classesTypeMap, typePool, INSTANCE_METHOD_DELEGATE_TEMPLATE, point.getMethodsInterceptor());
                }
            }

            for (ConstructorInterceptPoint point : define.getConstructorsInterceptPoints()) {
                generateDelegator(classesTypeMap, typePool, CONSTRUCTOR_DELEGATE_TEMPLATE, point.getConstructorInterceptor());
            }

            for (StaticMethodsInterceptPoint point : define.getStaticMethodsInterceptPoints()) {
                if (point.isOverrideArgs()) {
                    generateDelegator(classesTypeMap, typePool, STATIC_METHOD_WITH_OVERRIDE_ARGS_DELEGATE_TEMPLATE, point.getMethodsInterceptor());
                } else {
                    generateDelegator(classesTypeMap, typePool, STATIC_METHOD_DELEGATE_TEMPLATE, point.getMethodsInterceptor());
                }
            }
        }
        return bootstrapClassMatchDefines.size() > 0;
    }

    /**
     * Generate the delegator class based on given template class. This is preparation stage level code generation.
     *
     * One key step to avoid class confliction between AppClassLoader and BootstrapClassLoader
     *
     * @param classesTypeMap hosts injected binary of generated class
     * @param typePool to generate new class
     * @param templateClassName represents the class as template in this generation process. The templates are
     * pre-defined in SkyWalking agent core.
     * @param methodsInterceptor
     */
    private static void generateDelegator(Map<String, byte[]> classesTypeMap, TypePool typePool,
        String templateClassName, String methodsInterceptor) {
        String internalInterceptorName = internalDelegate(methodsInterceptor);
        try {
            TypeDescription templateTypeDescription = typePool.describe(templateClassName)
                .resolve();

            DynamicType.Unloaded interceptorType = new ByteBuddy()
                .redefine(templateTypeDescription, ClassFileLocator.ForClassLoader.of(BootstrapInstrumentBoost.class.getClassLoader()))
                .name(internalInterceptorName)
                .field(named("TARGET_INTERCEPTOR")).value(methodsInterceptor)
                .make();

            classesTypeMap.put(internalInterceptorName, interceptorType.getBytes());

            InstrumentDebuggingClass.INSTANCE.log(interceptorType);
        } catch (Exception e) {
            throw new PluginException("Generate Dynamic plugin failure", e);
        }
    }

    /**
     * The class loaded by this method means it only should be loaded once in Bootstrap classloader, when bootstrap
     * instrumentation active by any plugin
     *
     * @param loadedTypeMap hosts all injected class
     * @param className to load
     */
    private static void loadHighPriorityClass(Map<String, byte[]> loadedTypeMap,
        String className) throws PluginException {
        byte[] enhancedInstanceClassFile;
        try {
            String classResourceName = className.replaceAll("\\.", "/") + ".class";
            InputStream resourceAsStream = AgentClassLoader.getDefault().getResourceAsStream(classResourceName);

            if (resourceAsStream == null) {
                throw new PluginException("High priority class " + className + " not found.");
            }

            ByteArrayOutputStream os = new ByteArrayOutputStream();

            byte[] buffer = new byte[1024];
            int len;

            // read bytes from the input stream and store them in buffer
            while ((len = resourceAsStream.read(buffer)) != -1) {
                // write bytes from the buffer into output stream
                os.write(buffer, 0, len);
            }

            enhancedInstanceClassFile = os.toByteArray();
        } catch (IOException e) {
            throw new PluginException(e.getMessage(), e);
        }

        loadedTypeMap.put(className, enhancedInstanceClassFile);
    }
}
