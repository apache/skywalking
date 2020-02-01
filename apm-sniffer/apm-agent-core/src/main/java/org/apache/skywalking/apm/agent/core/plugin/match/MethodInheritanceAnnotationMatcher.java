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
package org.apache.skywalking.apm.agent.core.plugin.match;

import net.bytebuddy.build.HashCodeAndEqualsPlugin;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.annotation.AnnotationSource;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.method.ParameterList;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.matcher.CollectionItemMatcher;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Objects;

import static net.bytebuddy.matcher.ElementMatchers.annotationType;

/**
 * Matching used to match method annotations, Can match annotations on interface methods
 * @author jialong
 */
@HashCodeAndEqualsPlugin.Enhance
public class MethodInheritanceAnnotationMatcher<T extends MethodDescription> extends ElementMatcher.Junction.AbstractBase<T> {
    /**
     * The matcher to be applied to the provided annotation list.
     */
    private final ElementMatcher<? super AnnotationList> matcher;

    /**
     * Creates a new matcher for the annotations of an annotated element.
     *
     * @param matcher The matcher to be applied to the provided annotation list.
     */
    public MethodInheritanceAnnotationMatcher(ElementMatcher<? super AnnotationList> matcher) {
        this.matcher = matcher;
    }

    @Override
    public boolean matches(T target) {
        if (matcher.matches(target.getDeclaredAnnotations())) {
            return true;
        }
        String name = target.getName();
        ParameterList<?> parameters = target.getParameters();

        TypeDefinition declaringType = target.getDeclaringType();
        return recursiveMatches(declaringType, name, parameters);
    }


    private boolean recursiveMatches(TypeDefinition typeDefinition, String methodName, ParameterList<?> parameters) {
        TypeList.Generic interfaces = typeDefinition.getInterfaces();
        for (TypeDescription.Generic implInterface : interfaces) {
            if (recursiveMatches(implInterface, methodName, parameters)) {
                return true;
            }
            MethodList<MethodDescription.InGenericShape> declaredMethods = implInterface.getDeclaredMethods();
            for (MethodDescription declaredMethod : declaredMethods) {
                if (Objects.equals(declaredMethod.getName(), methodName) && parameterEquals(parameters, declaredMethod.getParameters())) {
                    return matcher.matches(declaredMethod.getDeclaredAnnotations());
                }
            }
        }
        return false;
    }


    private boolean parameterEquals(ParameterList<?> source, ParameterList<?> impl) {
        if (source.size() != impl.size()) {
            return false;
        }
        for (int i = 0; i < source.size(); i++) {
            if (!Objects.equals(source.get(i).getType(), impl.get(i).getType())) {
                return false;
            }
        }
        return true;
    }

    public static <T extends AnnotationSource> ElementMatcher.Junction<T> byMethodInheritanceAnnotationMatcher(ElementMatcher<? super TypeDescription> matcher) {
        return new MethodInheritanceAnnotationMatcher(new CollectionItemMatcher<>(annotationType(matcher)));
    }
}
