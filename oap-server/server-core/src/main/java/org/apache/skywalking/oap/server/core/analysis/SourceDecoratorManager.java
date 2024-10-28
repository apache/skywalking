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

package org.apache.skywalking.oap.server.core.analysis;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.source.ISource;

@Slf4j
public class SourceDecoratorManager {
    public static final Map<String, ISourceDecorator<ISource>> DECORATOR_MAP = new HashMap<>();

    public void addIfAsSourceDecorator(Class<?> aClass) throws IllegalAccessException, InstantiationException {
        if (!aClass.isInterface() && !Modifier.isAbstract(
            aClass.getModifiers()) && ISourceDecorator.class.isAssignableFrom(aClass)) {
            Type[] genericInterfaces = aClass.getGenericInterfaces();
            for (Type genericInterface : genericInterfaces) {
                ParameterizedType anInterface = (ParameterizedType) genericInterface;
                if (anInterface.getRawType().getTypeName().equals(ISourceDecorator.class.getName())) {
                    Type[] arguments = anInterface.getActualTypeArguments();

                    if (arguments.length != 1) {
                        throw new UnexpectedException("unexpected type argument number, class " + aClass.getName());
                    }
                    Type argument = arguments[0];

                    Object source = ((Class<?>) argument).newInstance();

                    if (!ISource.class.isAssignableFrom(source.getClass())) {
                        throw new UnexpectedException(
                            "unexpected type argument of class " + aClass.getName() + ", should be `org.apache.skywalking.oap.server.core.source.Source`. ");
                    }
                    ISourceDecorator<ISource> decorator = (ISourceDecorator) aClass.newInstance();
                    ISourceDecorator<ISource> exist = DECORATOR_MAP.put(aClass.getSimpleName(), decorator);
                    if (exist != null) {
                        throw new IllegalStateException(
                            "Conflict decorator names: The " + aClass.getName() + " class simple name is the same with " + exist.getClass().getName() +
                                ", please change the class simple name.");
                    }
                    log.info("Decorator {} is added into DefaultScopeDefine {}.", decorator.getClass()
                                                                                                .getName(), ((ISource) source).scope());
                }
            }
        }
    }
}
