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

package org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.bootstrap;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.Morph;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.skywalking.apm.agent.core.plugin.AbstractClassEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.EnhanceContext;
import org.apache.skywalking.apm.agent.core.plugin.PluginException;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.DeclaredInstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.EnhanceException;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.OverrideCallable;

import static net.bytebuddy.jar.asm.Opcodes.ACC_PRIVATE;
import static net.bytebuddy.jar.asm.Opcodes.ACC_VOLATILE;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassEnhancePluginDefine.CONTEXT_ATTR_NAME;

/**
 * This plugin define explicitly the instrumentation for JRE classes(mostly rt.jar). JRE class Instrumentation is more
 * class loader sensitive than other framework class instrumentation.
 *
 * The ways to instrument are more original, include more coding requirements.
 *
 * @author wusheng
 */
public abstract class BootstrapClassEnhancePluginDefine extends AbstractClassEnhancePluginDefine {
    @Override
    protected DynamicType.Builder<?> enhance(TypeDescription typeDescription, DynamicType.Builder<?> newClassBuilder,
        ClassLoader classLoader, EnhanceContext context) throws PluginException {
        newClassBuilder = this.enhanceInstance(typeDescription, newClassBuilder, classLoader, context);

        return newClassBuilder;
    }

    /**
     * Enhance a class to intercept constructors and class instance methods.
     *
     * @param typeDescription target class description
     * @param newClassBuilder byte-buddy's builder to manipulate class bytecode.
     * @return new byte-buddy's builder for further manipulation.
     */
    private DynamicType.Builder<?> enhanceInstance(TypeDescription typeDescription,
        DynamicType.Builder<?> newClassBuilder, ClassLoader classLoader,
        EnhanceContext context) throws PluginException {
//        ConstructorInterceptPoint[] constructorInterceptPoints = getConstructorsInterceptPoints();
        BootstrapInstanceMethodsInterceptPoint[] instanceMethodsInterceptPoints = getInstanceMethodsInterceptPoints();
        String enhanceOriginClassName = typeDescription.getTypeName();
        boolean existedConstructorInterceptPoint = false;
//        if (constructorInterceptPoints != null && constructorInterceptPoints.length > 0) {
//            existedConstructorInterceptPoint = true;
//        }
        boolean existedMethodsInterceptPoints = false;
        if (instanceMethodsInterceptPoints != null && instanceMethodsInterceptPoints.length > 0) {
            existedMethodsInterceptPoints = true;
        }

        /**
         * nothing need to be enhanced in class instance, maybe need enhance static methods.
         */
        if (!existedConstructorInterceptPoint && !existedMethodsInterceptPoints) {
            return newClassBuilder;
        }

        /**
         * Manipulate class source code.<br/>
         *
         * new class need:<br/>
         * 1.Add field, name {@link #CONTEXT_ATTR_NAME}.
         * 2.Add a field accessor for this field.
         *
         * And make sure the source codes manipulation only occurs once.
         *
         */
        if (!context.isObjectExtended()) {
            newClassBuilder = newClassBuilder.defineField(CONTEXT_ATTR_NAME, Object.class, ACC_PRIVATE | ACC_VOLATILE)
                .implement(EnhancedInstance.class)
                .intercept(FieldAccessor.ofField(CONTEXT_ATTR_NAME));
            context.extendObjectCompleted();
        }

        /**
         * 2. enhance constructors
         */
//        if (existedConstructorInterceptPoint) {
//            for (ConstructorInterceptPoint constructorInterceptPoint : constructorInterceptPoints) {
//                newClassBuilder = newClassBuilder.constructor(constructorInterceptPoint.getConstructorMatcher()).intercept(SuperMethodCall.INSTANCE
//                    .andThen(MethodDelegation.withDefaultConfiguration()
//                        .to(new ConstructorInter(constructorInterceptPoint.getConstructorInterceptor(), classLoader))
//                    )
//                );
//            }
//        }

        /**
         * 3. enhance instance methods
         */
        if (existedMethodsInterceptPoints) {
            for (BootstrapInstanceMethodsInterceptPoint instanceMethodsInterceptPoint : instanceMethodsInterceptPoints) {
                Class interceptor = instanceMethodsInterceptPoint.getMethodsInterceptor();
                if (interceptor == null) {
                    throw new EnhanceException("no BootstrapInstanceMethodsInterceptPoint define to enhance class " + enhanceOriginClassName);
                }

                ElementMatcher.Junction<MethodDescription> junction = not(isStatic()).and(instanceMethodsInterceptPoint.getMethodsMatcher());
                if (instanceMethodsInterceptPoint instanceof DeclaredInstanceMethodsInterceptPoint) {
                    junction = junction.and(ElementMatchers.<MethodDescription>isDeclaredBy(typeDescription));
                }
                if (instanceMethodsInterceptPoint.isOverrideArgs()) {
                    newClassBuilder =
                        newClassBuilder.method(junction)
                            .intercept(
                                MethodDelegation.withDefaultConfiguration()
                                    .withBinders(
                                        Morph.Binder.install(OverrideCallable.class)
                                    )
                                    .to(interceptor)
                            );
                } else {
                    newClassBuilder =
                        newClassBuilder.method(junction)
                            .intercept(
                                MethodDelegation.withDefaultConfiguration()
                                    .to(interceptor)
                            );
                }
            }
        }

        return newClassBuilder;
    }

    private void checkInstanceInterceptor(Class interceptor) {
        //TODO
    }

    public abstract BootstrapInstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints();
}
