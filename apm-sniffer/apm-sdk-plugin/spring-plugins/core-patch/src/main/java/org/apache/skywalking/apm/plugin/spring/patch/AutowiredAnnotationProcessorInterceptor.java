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

package org.apache.skywalking.apm.plugin.spring.patch;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

/**
 * {@link AutowiredAnnotationProcessorInterceptor} return the correct constructor when the bean class is enhanced by
 * skywalking.
 */
public class AutowiredAnnotationProcessorInterceptor implements InstanceMethodsAroundInterceptor, InstanceConstructorInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        Class<?> beanClass = (Class<?>) allArguments[0];
        if (EnhancedInstance.class.isAssignableFrom(beanClass)) {
            Map<Class<?>, Constructor<?>[]> candidateConstructorsCache = (Map<Class<?>, Constructor<?>[]>) objInst.getSkyWalkingDynamicField();

            Constructor<?>[] candidateConstructors = candidateConstructorsCache.get(beanClass);
            if (candidateConstructors == null) {
                Constructor<?>[] returnCandidateConstructors = (Constructor<?>[]) ret;

                /**
                 * The return for the method {@link org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor#determineCandidateConstructors(Class, String)
                 * contains three cases:
                 * 1. Constructors with annotation {@link org.springframework.beans.factory.annotation.Autowired}.
                 * 2. The bean class only has one constructor with parameters.
                 * 3. The bean has constructor without parameters.
                 *
                 * because of the manipulate mechanism generates another private constructor in the enhance class, all the class that constrcutor enhance by skywalking
                 * cannot go to case two, and it will go to case three. case one is not affected in the current manipulate mechanism situation.
                 *
                 * The interceptor fill out the private constructor when the class is enhanced by skywalking, and check if the remainder constructors size is equals one,
                 * if yes, return the constructor. or return constructor without parameters.
                 *
                 * @see org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor#determineCandidateConstructors(Class, String)
                 */
                if (returnCandidateConstructors == null) {
                    Constructor<?>[] rawConstructor = beanClass.getDeclaredConstructors();
                    List<Constructor<?>> candidateRawConstructors = new ArrayList<Constructor<?>>();
                    for (Constructor<?> constructor : rawConstructor) {
                        if (!Modifier.isPrivate(constructor.getModifiers())) {
                            candidateRawConstructors.add(constructor);
                        }
                    }

                    if (candidateRawConstructors.size() == 1 && candidateRawConstructors.get(0)
                                                                                        .getParameterTypes().length > 0) {
                        candidateConstructors = new Constructor<?>[] {candidateRawConstructors.get(0)};
                    } else {
                        candidateConstructors = new Constructor<?>[0];
                    }

                } else {
                    candidateConstructors = returnCandidateConstructors;
                }

                candidateConstructorsCache.put(beanClass, candidateConstructors);
            }

            return candidateConstructors.length > 0 ? candidateConstructors : null;
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        Map<Class<?>, Constructor<?>[]> candidateConstructorsCache = new ConcurrentHashMap<Class<?>, Constructor<?>[]>(20);
        objInst.setSkyWalkingDynamicField(candidateConstructorsCache);
    }
}
