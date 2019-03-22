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

package org.apache.skywalking.oap.server.core.source;

import java.lang.annotation.Annotation;
import java.util.*;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.annotation.AnnotationListener;

/**
 * @author peng-yongsheng, wusheng
 */

public class DefaultScopeDefine {
    private static final Map<String, Integer> NAME_2_ID = new HashMap<>();
    private static final Map<Integer, String> ID_2_NAME = new HashMap<>();

    /**
     * All metric IDs in [0, 10,000) are reserved in Apache SkyWalking.
     *
     * If you want to extend the scope, recommend to start with 10,000.
     */
    public static final int ALL = 0;
    public static final int SERVICE = 1;
    public static final int SERVICE_INSTANCE = 2;
    public static final int ENDPOINT = 3;
    public static final int SERVICE_RELATION = 4;
    public static final int SERVICE_INSTANCE_RELATION = 5;
    public static final int ENDPOINT_RELATION = 6;
    public static final int NETWORK_ADDRESS = 7;
    public static final int SERVICE_INSTANCE_JVM_CPU = 8;
    public static final int SERVICE_INSTANCE_JVM_MEMORY = 9;
    public static final int SERVICE_INSTANCE_JVM_MEMORY_POOL = 10;
    public static final int SERVICE_INSTANCE_JVM_GC = 11;
    public static final int SEGMENT = 12;
    public static final int ALARM = 13;
    public static final int SERVICE_INVENTORY = 14;
    public static final int SERVICE_INSTANCE_INVENTORY = 15;
    public static final int ENDPOINT_INVENTORY = 16;
    public static final int DATABASE_ACCESS = 17;
    public static final int DATABASE_SLOW_STATEMENT = 18;
    public static final int SERVICE_INSTANCE_CLR_CPU = 19;
    public static final int SERVICE_INSTANCE_CLR_GC = 20;
    public static final int SERVICE_INSTANCE_CLR_THREAD = 21;
    public static final int ENVOY_INSTANCE_METRIC = 22;

    /**
     * Catalog of scope, the indicator processor could use this to group all generated indicators by oal tool.
     */
    public static final String SERVICE_CATALOG_NAME = "SERVICE";
    public static final String SERVICE_INSTANCE_CATALOG_NAME = "SERVICE_INSTANCE";
    public static final String ENDPOINT_CATALOG_NAME = "ENDPOINT";

    private static final Map<Integer, Boolean> SERVICE_CATALOG = new HashMap<>();
    private static final Map<Integer, Boolean> SERVICE_INSTANCE_CATALOG = new HashMap<>();
    private static final Map<Integer, Boolean> ENDPOINT_CATALOG = new HashMap<>();

    public static class Listener implements AnnotationListener {
        @Override public Class<? extends Annotation> annotation() {
            return ScopeDeclaration.class;
        }

        @Override public void notify(Class originalClass) {
            ScopeDeclaration declaration = (ScopeDeclaration)originalClass.getAnnotation(ScopeDeclaration.class);
            if (declaration != null) {
                addNewScope(declaration, originalClass);
            }
        }
    }

    public static final void addNewScope(ScopeDeclaration declaration, Class originalClass) {
        int id = declaration.id();
        if (ID_2_NAME.containsKey(id)) {
            throw new UnexpectedException("ScopeDeclaration id=" + id + " at " + originalClass.getName() + " has conflict with another named " + ID_2_NAME.get(id));
        }
        String name = declaration.name();
        if (NAME_2_ID.containsKey(name)) {
            throw new UnexpectedException("ScopeDeclaration name=" + name + " at " + originalClass.getName() + " has conflict with another id= " + NAME_2_ID.get(name));
        }
        ID_2_NAME.put(id, name);
        NAME_2_ID.put(name, id);

        String catalogName = declaration.catalog();
        switch (catalogName) {
            case SERVICE_CATALOG_NAME:
                SERVICE_CATALOG.put(id, Boolean.TRUE);
                break;
            case SERVICE_INSTANCE_CATALOG_NAME:
                SERVICE_INSTANCE_CATALOG.put(id, Boolean.TRUE);
                break;
            case ENDPOINT_CATALOG_NAME:
                ENDPOINT_CATALOG.put(id, Boolean.TRUE);
                break;
        }
    }

    public static String nameOf(int id) {
        String name = ID_2_NAME.get(id);
        if (name == null) {
            throw new UnexpectedException("ScopeDefine id = " + id + " not found.");
        }
        return name;
    }

    public static int valueOf(String name) {
        Integer id = NAME_2_ID.get(name);
        if (id == null) {
            throw new UnexpectedException("ScopeDefine name = " + name + " not found.");
        }
        return id;
    }

    public static void reset() {
        NAME_2_ID.clear();
        ID_2_NAME.clear();
    }

    public static boolean inServiceCatalog(int scopeId) {
        return SERVICE_CATALOG.containsKey(scopeId);
    }

    public static boolean inServiceInstanceCatalog(int scopeId) {
        return SERVICE_INSTANCE_CATALOG.containsKey(scopeId);
    }

    public static boolean inEndpointCatalog(int scopeId) {
        return ENDPOINT_CATALOG.containsKey(scopeId);
    }
}
