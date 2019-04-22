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

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * Match the class by the given super class or interfaces.
 *
 * @author wusheng
 */
public class HierarchyMatch implements IndirectMatch {
    private String[] parentTypes;
    private String[] skipTypes;

    private HierarchyMatch(String[] parentTypes, String[] skipTypes) {
        if (parentTypes == null || parentTypes.length == 0) {
            throw new IllegalArgumentException("parentTypes is null");
        }
        this.parentTypes = parentTypes;
        this.skipTypes = skipTypes;
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
        if (skipTypes != null) {
            List<String> skipTypes = new ArrayList<String>(Arrays.asList(this.skipTypes));
            for (String skipType : skipTypes) {
                if (skipType.equals(typeDescription.getName())) {
                    return false;
                }
            }
        }

        List<String> parentTypes = new ArrayList<String>(Arrays.asList(this.parentTypes));

        TypeList.Generic implInterfaces = typeDescription.getInterfaces();
        for (TypeDescription.Generic implInterface : implInterfaces) {
            matchHierarchyClass(implInterface, parentTypes);
        }

        if (typeDescription.getSuperClass() != null) {
            matchHierarchyClass(typeDescription.getSuperClass(), parentTypes);
        }

        if (parentTypes.size() == 0) {
            return true;
        }

        return false;
    }

    private void matchHierarchyClass(TypeDescription.Generic clazz, List<String> parentTypes) {
        parentTypes.remove(clazz.asRawType().getTypeName());
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
        return byHierarchyMatch(parentTypes, null);
    }

    public static ClassMatch byHierarchyMatch(String[] parentTypes, String[] skipTypes) {
        return new HierarchyMatch(parentTypes, skipTypes);
    }
}
