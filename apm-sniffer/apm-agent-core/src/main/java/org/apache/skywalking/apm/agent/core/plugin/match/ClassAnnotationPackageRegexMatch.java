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
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameMatches;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * Match the class by the given annotations and regex expression matching package name.
 */
public class ClassAnnotationPackageRegexMatch implements IndirectMatch {
    private String[] annotations;
    private String[] regexExpressions;

    private ClassAnnotationPackageRegexMatch(String[] annotations, String[] regexExpressions) {
        if (annotations == null || annotations.length == 0 || regexExpressions == null || regexExpressions.length == 0) {
            throw new IllegalArgumentException("annotations is null");
        }
        this.annotations = annotations;
        this.regexExpressions = regexExpressions;
    }

    @Override
    public ElementMatcher.Junction buildJunction() {
        ElementMatcher.Junction annotationJunction = null;
        for (String annotation : annotations) {
            if (annotationJunction == null) {
                annotationJunction = buildEachAnnotation(annotation);
            } else {
                annotationJunction = annotationJunction.and(buildEachAnnotation(annotation));
            }
        }
        ElementMatcher.Junction nameJunction = null;
        for (String regexExpression : regexExpressions) {
            if (nameJunction == null) {
                nameJunction = buildEachMatchExpression(regexExpression);
            } else {
                nameJunction = nameJunction.or(buildEachMatchExpression(regexExpression));
            }
        }
        return annotationJunction.and(not(isInterface())).and(nameJunction);
    }

    @Override
    public boolean isMatch(TypeDescription typeDescription) {
        List<String> annotationList = new ArrayList<>(Arrays.asList(annotations));
        AnnotationList declaredAnnotations = typeDescription.getDeclaredAnnotations();
        for (AnnotationDescription annotation : declaredAnnotations) {
            annotationList.remove(annotation.getAnnotationType().getActualName());
        }
        boolean isAnnotationMatch = annotationList.isEmpty();
        boolean isPackageNameRegexMatch = false;
        for (String matchExpression : regexExpressions) {
            isPackageNameRegexMatch = isPackageNameRegexMatch || typeDescription.getTypeName().matches(matchExpression);
        }
        return isAnnotationMatch && isPackageNameRegexMatch;
    }

    private ElementMatcher.Junction buildEachAnnotation(String annotationName) {
        return isAnnotatedWith(named(annotationName));
    }

    private ElementMatcher.Junction buildEachMatchExpression(String matchExpression) {
        return nameMatches(matchExpression);
    }

    public static ClassMatch byClassAnnotationAndRegexMatch(String[] annotations, String[] regexExpressions) {
        return new ClassAnnotationPackageRegexMatch(annotations, regexExpressions);
    }
}
