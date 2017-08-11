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
