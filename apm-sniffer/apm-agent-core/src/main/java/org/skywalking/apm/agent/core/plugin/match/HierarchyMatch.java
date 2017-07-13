package org.skywalking.apm.agent.core.plugin.match;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * Match the class by the given super class or interfaces.
 *
 * @author wusheng
 */
public class HierarchyMatch implements IndirectMatch {
    private String[] parentTypes;

    private HierarchyMatch(String[] parentTypes) {
        if (parentTypes == null || parentTypes.length == 0) {
            throw new IllegalArgumentException("parentTypes is null");
        }
        this.parentTypes = parentTypes;
    }

    @Override
    public ElementMatcher.Junction buildJunction() {
        ElementMatcher.Junction junction = null;
        for (String superTypeName : parentTypes) {
            if (junction == null) {
                junction = buildEachAnnotation(superTypeName);
            } else {
                junction = junction.and(buildEachAnnotation(superTypeName));
            }
        }
        junction = junction.and(not(isInterface()));
        return junction;
    }

    private ElementMatcher.Junction buildEachAnnotation(String superTypeName) {
        return hasSuperType(named(superTypeName));
    }

    @Override
    public boolean isMatch(TypeDescription typeDescription) {
        return false;
    }

    public static ClassMatch byHierarchyMatch(String[] parentTypes) {
        return new HierarchyMatch(parentTypes);
    }
}
