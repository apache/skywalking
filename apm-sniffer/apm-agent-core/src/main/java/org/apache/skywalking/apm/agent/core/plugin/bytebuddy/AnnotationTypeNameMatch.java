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
package org.apache.skywalking.apm.agent.core.plugin.bytebuddy;

import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationSource;
import net.bytebuddy.matcher.CollectionItemMatcher;
import net.bytebuddy.matcher.DeclaringAnnotationMatcher;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * @author AI
 * 2019-08-15
 */
public class AnnotationTypeNameMatch<T extends AnnotationDescription> implements ElementMatcher<T> {

    private String annotationTypeName;

    private AnnotationTypeNameMatch(String annotationTypeName) {
        this.annotationTypeName = annotationTypeName;
    }

    @Override
    public boolean matches(T target) {
        return target.getAnnotationType().asErasure().getName().equals(annotationTypeName);
    }

    public static <T extends AnnotationSource> ElementMatcher.Junction<T> isAnnotatedWithType(String annotationTypeName) {
        final AnnotationTypeNameMatch<AnnotationDescription> matcher = new AnnotationTypeNameMatch<AnnotationDescription>(annotationTypeName);
        return new DeclaringAnnotationMatcher<T>(new CollectionItemMatcher<AnnotationDescription>(matcher));
    }
}
