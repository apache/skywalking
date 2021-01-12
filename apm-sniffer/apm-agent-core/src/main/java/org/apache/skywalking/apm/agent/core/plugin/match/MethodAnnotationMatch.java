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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Match the class, which has methods with the certain annotations. This is a very complex match.
 */
public class MethodAnnotationMatch implements IndirectMatch {
    private String[] annotations;

    private MethodAnnotationMatch(String[] annotations) {
        if (annotations == null || annotations.length == 0) {
            throw new IllegalArgumentException("annotations is null");
        }
        this.annotations = annotations;
    }

    @Override
    public ElementMatcher.Junction buildJunction() {
        ElementMatcher.Junction junction = null;
        for (String annotation : annotations) {
            if (junction == null) {
                junction = buildEachAnnotation(annotation);
            } else {
                junction = junction.and(buildEachAnnotation(annotation));
            }
        }
        junction = declaresMethod(junction).and(ElementMatchers.not(isInterface()));
        return junction;
    }

    @Override
    public boolean isMatch(TypeDescription typeDescription) {
        for (MethodDescription.InDefinedShape methodDescription : typeDescription.getDeclaredMethods()) {
            List<String> annotationList = new ArrayList<String>(Arrays.asList(annotations));

            AnnotationList declaredAnnotations = methodDescription.getDeclaredAnnotations();
            for (AnnotationDescription annotation : declaredAnnotations) {
                annotationList.remove(annotation.getAnnotationType().getActualName());
            }
            if (annotationList.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private ElementMatcher.Junction buildEachAnnotation(String annotationName) {
        return isAnnotatedWith(named(annotationName));
    }

    public static IndirectMatch byMethodAnnotationMatch(String... annotations) {
        return new MethodAnnotationMatch(annotations);
    }
}
