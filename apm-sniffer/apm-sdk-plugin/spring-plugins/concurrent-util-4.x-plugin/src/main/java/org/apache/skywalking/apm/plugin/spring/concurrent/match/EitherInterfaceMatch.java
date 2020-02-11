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

package org.apache.skywalking.apm.plugin.spring.concurrent.match;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.match.IndirectMatch;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * {@link EitherInterfaceMatch} match the class inherited {@link #getMatchInterface() } and not inherited {@link
 * #getMutexInterface()}
 */
public abstract class EitherInterfaceMatch implements IndirectMatch {

    private static final String SPRING_PACKAGE_PREFIX = "org.springframework";
    private static final String OBJECT_CLASS_NAME = "java.lang.Object";

    protected EitherInterfaceMatch() {

    }

    @Override
    public ElementMatcher.Junction buildJunction() {
        return not(nameStartsWith(SPRING_PACKAGE_PREFIX)).
                                                             and(hasSuperType(named(getMatchInterface())))
                                                         .and(not(hasSuperType(named(getMutexInterface()))));
    }

    @Override
    public boolean isMatch(TypeDescription typeDescription) {
        MatchResult matchResult = new MatchResult();
        for (TypeDescription.Generic generic : typeDescription.getInterfaces()) {
            matchHierarchyClazz(generic, matchResult);
        }

        if (typeDescription.getSuperClass() != null) {
            matchHierarchyClazz(typeDescription.getSuperClass(), matchResult);
        }

        return matchResult.result();
    }

    public abstract String getMatchInterface();

    public abstract String getMutexInterface();

    private void matchHierarchyClazz(TypeDescription.Generic clazz, MatchResult matchResult) {
        if (clazz.asRawType().getTypeName().equals(getMutexInterface())) {
            matchResult.findMutexInterface = true;
            return;
        }

        if (clazz.asRawType().getTypeName().equals(getMatchInterface())) {
            matchResult.findMatchInterface = true;
        }

        for (TypeDescription.Generic generic : clazz.getInterfaces()) {
            matchHierarchyClazz(generic, matchResult);
        }

        TypeDescription.Generic superClazz = clazz.getSuperClass();
        if (superClazz != null && !clazz.getTypeName().equals(OBJECT_CLASS_NAME)) {
            matchHierarchyClazz(superClazz, matchResult);
        }
    }

    private static class MatchResult {
        private boolean findMatchInterface = false;
        private boolean findMutexInterface = false;

        public boolean result() {
            return findMatchInterface && !findMutexInterface;
        }
    }
}
