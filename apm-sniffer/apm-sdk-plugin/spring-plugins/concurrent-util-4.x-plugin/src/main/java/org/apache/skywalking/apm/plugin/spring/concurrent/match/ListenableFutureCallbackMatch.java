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
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.IndirectMatch;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * {@link ListenableFutureCallbackMatch} match the class that inherited <code>org.springframework.util.concurrent.ListenableFutureCallback</code>.
 */
public class ListenableFutureCallbackMatch implements IndirectMatch {

    private static final String LISTENABLE_FUTURE_CALLBACK_CLASS_NAME = "org.springframework.util.concurrent.ListenableFutureCallback";

    private ListenableFutureCallbackMatch() {

    }

    @Override
    public ElementMatcher.Junction buildJunction() {
        return not(nameStartsWith("org.springframework")).
                                                             and(hasSuperType(named(LISTENABLE_FUTURE_CALLBACK_CLASS_NAME)));
    }

    @Override
    public boolean isMatch(TypeDescription typeDescription) {
        boolean isMatch = false;
        for (TypeDescription.Generic generic : typeDescription.getInterfaces()) {
            isMatch = isMatch || matchExactClass(generic);
        }

        if (typeDescription.getSuperClass() != null) {
            return isMatch || matchExactClass(typeDescription.getSuperClass());
        } else {
            return isMatch;
        }
    }

    private boolean matchExactClass(TypeDescription.Generic clazz) {
        if (clazz.asRawType().getTypeName().equals(LISTENABLE_FUTURE_CALLBACK_CLASS_NAME)) {
            return true;
        }

        boolean isMatch = false;
        for (TypeDescription.Generic generic : clazz.getInterfaces()) {
            isMatch = isMatch || matchExactClass(generic);
        }

        if (!isMatch) {
            TypeDescription.Generic superClazz = clazz.getSuperClass();
            if (superClazz != null && !clazz.getTypeName().equals("java.lang.Object")) {
                isMatch = isMatch || matchExactClass(superClazz);
            }
        }

        return isMatch;
    }

    public static ClassMatch listenableFutureCallbackMatch() {
        return new ListenableFutureCallbackMatch();
    }
}
