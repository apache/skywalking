package org.skywalking.apm.agent.core.plugin.match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;
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
                junction = buildSuperClassMatcher(superTypeName);
            } else {
                junction = junction.and(buildSuperClassMatcher(superTypeName));
            }
        }
        junction = junction.and(not(isInterface()));
        return junction;
    }

    private ElementMatcher.Junction buildSuperClassMatcher(String superTypeName) {
        return hasSuperType(named(superTypeName));
    }

    @Override
    public boolean isMatch(TypeDescription typeDescription) {
        List<String> parentTypes = new ArrayList<String>(Arrays.asList(this.parentTypes));

        TypeList.Generic implInterfaces = typeDescription.getInterfaces();
        for (TypeDescription.Generic implInterface : implInterfaces) {
            matchHierarchyClass(implInterface, parentTypes);
        }

        matchHierarchyClass(typeDescription.getSuperClass(), parentTypes);

        if (parentTypes.size() == 0) {
            return true;
        }

        return false;
    }

    private void matchHierarchyClass(TypeDescription.Generic clazz, List<String> parentTypes) {
        parentTypes.remove(clazz.getTypeName());
        if (parentTypes.size() == 0) {
            return;
        }

        for (TypeDescription.Generic generic : clazz.getInterfaces()) {
            matchHierarchyClass(generic, parentTypes);
        }

        TypeDescription.Generic superClazz = clazz.getSuperClass();
        if (superClazz != null && !clazz.getTypeName().equals("java.lang.Object")) {
            matchHierarchyClass(superClazz, parentTypes);
        }

    }

    public static ClassMatch byHierarchyMatch(String[] parentTypes) {
        return new HierarchyMatch(parentTypes);
    }
}
