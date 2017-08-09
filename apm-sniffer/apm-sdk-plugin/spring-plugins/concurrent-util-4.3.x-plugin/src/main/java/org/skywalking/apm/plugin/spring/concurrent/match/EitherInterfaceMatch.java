package org.skywalking.apm.plugin.spring.concurrent.match;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.skywalking.apm.agent.core.plugin.match.IndirectMatch;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * {@link EitherInterfaceMatch} match the class inherited {@link #getMatchInterface() } and not inherited {@link
 * #getMutexInterface()}
 *
 * @author zhangxin
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

        matchHierarchyClazz(typeDescription.getSuperClass(), matchResult);
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
