/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.plugin.spring.concurrent.match;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.skywalking.apm.agent.core.plugin.match.IndirectMatch;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * {@link ListenableFutureCallbackMatch} match the class that inherited <code>org.springframework.util.concurrent.ListenableFutureCallback</code>.
 *
 * @author zhangxin
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

        return isMatch || matchExactClass(typeDescription.getSuperClass());
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
