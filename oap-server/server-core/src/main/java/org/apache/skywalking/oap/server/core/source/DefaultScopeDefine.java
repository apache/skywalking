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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.annotation.AnnotationListener;

public class DefaultScopeDefine {
    private static final Map<String, Integer> NAME_2_ID = new HashMap<>();
    private static final Map<Integer, String> ID_2_NAME = new HashMap<>();
    private static final Map<String, List<ScopeDefaultColumn>> SCOPE_COLUMNS = new HashMap<>();

    /**
     * All metrics IDs in [0, 10,000) are reserved in Apache SkyWalking.
     * <p>
     * If you want to extend the scope, recommend to start with 10,000.
     */

    /**
     * @since 9.0.0
     */
    public static final int UNKNOWN = 0;
    /**
     * @since Deprecated from 9.0.0
     */
    @Deprecated
    public static final int ALL = 0;
    public static final int SERVICE = 1;
    public static final int SERVICE_INSTANCE = 2;
    public static final int ENDPOINT = 3;
    public static final int SERVICE_RELATION = 4;
    public static final int SERVICE_INSTANCE_RELATION = 5;
    public static final int ENDPOINT_RELATION = 6;
    public static final int SERVICE_INSTANCE_JVM_CPU = 8;
    public static final int SERVICE_INSTANCE_JVM_MEMORY = 9;
    public static final int SERVICE_INSTANCE_JVM_MEMORY_POOL = 10;
    public static final int SERVICE_INSTANCE_JVM_GC = 11;
    public static final int SEGMENT = 12;
    public static final int ALARM = 13;
    public static final int DATABASE_ACCESS = 17;
    public static final int DATABASE_SLOW_STATEMENT = 18;
    public static final int SERVICE_INSTANCE_CLR_CPU = 19;
    public static final int SERVICE_INSTANCE_CLR_GC = 20;
    public static final int SERVICE_INSTANCE_CLR_THREAD = 21;
    public static final int ENVOY_INSTANCE_METRIC = 22;
    public static final int ZIPKIN_SPAN = 23;
    @Deprecated
    public static final int JAEGER_SPAN = 24;
    @Deprecated
    public static final int HTTP_ACCESS_LOG = 25;
    public static final int PROFILE_TASK = 26;
    public static final int PROFILE_TASK_LOG = 27;
    public static final int PROFILE_TASK_SEGMENT_SNAPSHOT = 28;
    public static final int SERVICE_META = 29;
    public static final int SERVICE_INSTANCE_UPDATE = 30;
    public static final int NETWORK_ADDRESS_ALIAS = 31;
    public static final int UI_TEMPLATE = 32;
    public static final int SERVICE_INSTANCE_JVM_THREAD = 33;

    // browser
    public static final int BROWSER_ERROR_LOG = 34;
    public static final int BROWSER_APP_PERF = 35;
    public static final int BROWSER_APP_PAGE_PERF = 36;
    public static final int BROWSER_APP_SINGLE_VERSION_PERF = 37;
    public static final int BROWSER_APP_TRAFFIC = 38;
    public static final int BROWSER_APP_SINGLE_VERSION_TRAFFIC = 39;
    public static final int BROWSER_APP_PAGE_TRAFFIC = 40;

    public static final int LOG = 41;
    public static final int ENDPOINT_META = 42;

    public static final int EVENT = 43;

    public static final int SERVICE_INSTANCE_JVM_CLASS = 44;

    public static final int PROCESS = 45;
    public static final int EBPF_PROFILING_TASK = 46;
    public static final int EBPF_PROFILING_SCHEDULE = 47;
    public static final int EBPF_PROFILING_DATA = 48;
    public static final int SERVICE_LABEL = 49;
    public static final int TAG_AUTOCOMPLETE = 50;
    public static final int ZIPKIN_SERVICE = 51;
    public static final int ZIPKIN_SERVICE_SPAN = 52;
    public static final int ZIPKIN_SERVICE_RELATION = 53;
    public static final int PROCESS_RELATION = 54;
    public static final int CACHE_ACCESS = 55;
    public static final int CACHE_SLOW_ACCESS = 56;

    public static final int TCP_SERVICE = 57;
    public static final int TCP_SERVICE_INSTANCE = 58;
    public static final int TCP_SERVICE_RELATION = 59;
    public static final int TCP_SERVICE_INSTANCE_RELATION = 60;
    public static final int TCP_SERVICE_INSTANCE_UPDATE = 61;
    public static final int SAMPLED_SLOW_TRACE = 62;

    public static final int MESSAGE_QUEUE_ACCESS = 63;
    public static final int MESSAGE_QUEUE_ENDPOINT_ACCESS = 64;

    public static final int SPAN_ATTACHED_EVENT = 65;
    public static final int SAMPLED_STATUS_4XX_TRACE = 66;
    public static final int SAMPLED_STATUS_5XX_TRACE = 67;

    public static final int CONTINUOUS_PROFILING_POLICY = 68;

    public static final int UI_MENU = 69;

    public static final int SERVICE_HIERARCHY_RELATION = 70;
    public static final int INSTANCE_HIERARCHY_RELATION = 71;

    public static final int K8S_SERVICE = 72;
    public static final int K8S_SERVICE_INSTANCE = 73;
    public static final int K8S_SERVICE_RELATION = 74;
    public static final int K8S_SERVICE_INSTANCE_RELATION = 75;
    public static final int K8S_ENDPOINT = 76;

    public static final int CILIUM_SERVICE = 78;
    public static final int CILIUM_SERVICE_INSTANCE = 79;
    public static final int CILIUM_SERVICE_RELATION = 80;
    public static final int CILIUM_SERVICE_INSTANCE_RELATION = 81;
    public static final int CILIUM_ENDPOINT = 82;
    public static final int CILIUM_ENDPOINT_REALATION = 83;

    public static final int JFR_PROFILING_DATA = 84;
    public static final int ASYNC_PROFILER_TASK = 85;
    public static final int ASYNC_PROFILER_TASK_LOG = 86;

    public static final int BROWSER_APP_WEB_VITALS_PAGE_PERF = 87;
    public static final int BROWSER_APP_RESOURCE_PERF = 88;
    public static final int BROWSER_APP_WEB_INTERACTION_PAGE_PERF = 89;
    public static final int SW_SPAN_ATTACHED_EVENT = 90;

    /**
     * Catalog of scope, the metrics processor could use this to group all generated metrics by oal rt.
     */
    public static final String SERVICE_CATALOG_NAME = "SERVICE";
    public static final String SERVICE_INSTANCE_CATALOG_NAME = "SERVICE_INSTANCE";
    public static final String ENDPOINT_CATALOG_NAME = "ENDPOINT";
    public static final String SERVICE_RELATION_CATALOG_NAME = "SERVICE_RELATION";
    public static final String SERVICE_INSTANCE_RELATION_CATALOG_NAME = "SERVICE_INSTANCE_RELATION";
    public static final String ENDPOINT_RELATION_CATALOG_NAME = "ENDPOINT_RELATION";
    public static final String PROCESS_CATALOG_NAME = "PROCESS";
    public static final String PROCESS_RELATION_CATALOG_NAME = "PROCESS_RELATION";

    private static final Map<Integer, Boolean> SERVICE_CATALOG = new HashMap<>();
    private static final Map<Integer, Boolean> SERVICE_INSTANCE_CATALOG = new HashMap<>();
    private static final Map<Integer, Boolean> ENDPOINT_CATALOG = new HashMap<>();
    private static final Map<Integer, Boolean> SERVICE_RELATION_CATALOG = new HashMap<>();
    private static final Map<Integer, Boolean> SERVICE_INSTANCE_RELATION_CATALOG = new HashMap<>();
    private static final Map<Integer, Boolean> ENDPOINT_RELATION_CATALOG = new HashMap<>();
    private static final Map<Integer, Boolean> PROCESS_CATALOG = new HashMap<>();
    private static final Map<Integer, Boolean> PROCESS_RELATION_CATALOG = new HashMap<>();

    @Setter
    private static boolean ACTIVE_EXTRA_MODEL_COLUMNS = false;

    public static void activeExtraModelColumns() {
        ACTIVE_EXTRA_MODEL_COLUMNS = true;
    }

    /**
     * Annotation scan listener
     */
    public static class Listener implements AnnotationListener {
        @Override
        public Class<? extends Annotation> annotation() {
            return ScopeDeclaration.class;
        }

        @Override
        public void notify(Class originalClass) {
            ScopeDeclaration declaration = (ScopeDeclaration) originalClass.getAnnotation(ScopeDeclaration.class);
            if (declaration != null) {
                addNewScope(declaration, originalClass);
            }
        }
    }

    /**
     * Add a new scope based on the scan result
     *
     * @param declaration   includes the definition.
     * @param originalClass represents the class having the {@link ScopeDeclaration} annotation
     */
    private static void addNewScope(ScopeDeclaration declaration, Class originalClass) {
        int id = declaration.id();
        if (ID_2_NAME.containsKey(id)) {
            throw new UnexpectedException(
                "ScopeDeclaration id=" + id + " at " + originalClass.getName() + " has conflict with another named " + ID_2_NAME
                    .get(id));
        }
        if (id < 0) {
            throw new UnexpectedException(
                "ScopeDeclaration id=" + id + " at " + originalClass.getName() + " is negative. ");
        }
        String name = declaration.name();
        if (NAME_2_ID.containsKey(name)) {
            throw new UnexpectedException(
                "ScopeDeclaration fieldName=" + name + " at " + originalClass.getName() + " has conflict with another id= " + NAME_2_ID
                    .get(name));
        }

        ID_2_NAME.put(id, name);
        NAME_2_ID.put(name, id);

        List<ScopeDefaultColumn> scopeDefaultColumns = new ArrayList<>();

        ScopeDefaultColumn.VirtualColumnDefinition virtualColumn = (ScopeDefaultColumn.VirtualColumnDefinition) originalClass
            .getAnnotation(ScopeDefaultColumn.VirtualColumnDefinition.class);
        if (virtualColumn != null) {
            scopeDefaultColumns.add(
                new ScopeDefaultColumn(virtualColumn.fieldName(), virtualColumn.columnName(), virtualColumn
                    .type(), virtualColumn.isID(), virtualColumn.length(), -1, false));
        }
        Field[] scopeClassField = originalClass.getDeclaredFields();
        if (scopeClassField != null) {
            for (Field field : scopeClassField) {
                ScopeDefaultColumn.DefinedByField definedByField = field.getAnnotation(
                    ScopeDefaultColumn.DefinedByField.class);
                ScopeDefaultColumn.BanyanDB banyanDB = field.getAnnotation(
                    ScopeDefaultColumn.BanyanDB.class);
                int shardingKeyIdx = -1;
                if (banyanDB != null) {
                    shardingKeyIdx = banyanDB.shardingKeyIdx();
                }
                if (definedByField != null) {
                    if (!definedByField.requireDynamicActive() || ACTIVE_EXTRA_MODEL_COLUMNS) {
                        scopeDefaultColumns.add(
                            new ScopeDefaultColumn(
                                field.getName(), definedByField.columnName(), field.getType(), false,
                                definedByField.length(), shardingKeyIdx, definedByField.isAttribute()
                            ));
                    }
                }
            }
        }

        SCOPE_COLUMNS.put(name, scopeDefaultColumns);

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
            case SERVICE_RELATION_CATALOG_NAME:
                SERVICE_RELATION_CATALOG.put(id, Boolean.TRUE);
                break;
            case SERVICE_INSTANCE_RELATION_CATALOG_NAME:
                SERVICE_INSTANCE_RELATION_CATALOG.put(id, Boolean.TRUE);
                break;
            case ENDPOINT_RELATION_CATALOG_NAME:
                ENDPOINT_RELATION_CATALOG.put(id, Boolean.TRUE);
                break;
            case PROCESS_CATALOG_NAME:
                PROCESS_CATALOG.put(id, Boolean.TRUE);
                break;
            case PROCESS_RELATION_CATALOG_NAME:
                PROCESS_RELATION_CATALOG.put(id, Boolean.TRUE);
                break;
        }
    }

    /**
     * Fetch the name from given id
     *
     * @param id represents an existing scope id.
     * @return scope name.
     */
    public static String nameOf(int id) {
        String name = ID_2_NAME.get(id);
        if (name == null) {
            throw new UnexpectedException("ScopeDefine id = " + id + " not found.");
        }
        return name;
    }

    /**
     * Fetch the id of given name
     *
     * @param name represents an existing scope name
     * @return scope id
     */
    public static int valueOf(String name) {
        Integer id = NAME_2_ID.get(name);
        if (id == null) {
            throw new UnexpectedException("ScopeDefine fieldName = " + name + " not found.");
        }
        return id;
    }

    /**
     * Reset all existing scope definitions. For test only.
     */
    public static void reset() {
        NAME_2_ID.clear();
        ID_2_NAME.clear();
        SCOPE_COLUMNS.clear();
    }

    /**
     * Check whether the given scope ID belongs service catalog
     *
     * @param scopeId represents an existing scope id.
     * @return true is current scope set {@link ScopeDeclaration#catalog()} == {@link #SERVICE_CATALOG_NAME}
     */
    public static boolean inServiceCatalog(int scopeId) {
        return SERVICE_CATALOG.containsKey(scopeId);
    }

    /**
     * Check whether the given scope ID belongs service instance catalog
     *
     * @param scopeId represents an existing scope id.
     * @return true is current scope set {@link ScopeDeclaration#catalog()} == {@link #SERVICE_INSTANCE_CATALOG_NAME}
     */
    public static boolean inServiceInstanceCatalog(int scopeId) {
        return SERVICE_INSTANCE_CATALOG.containsKey(scopeId);
    }

    /**
     * Check whether the given scope ID belongs endpoint catalog
     *
     * @param scopeId represents an existing scope id.
     * @return true is current scope set {@link ScopeDeclaration#catalog()} == {@link #ENDPOINT_CATALOG_NAME}
     */
    public static boolean inEndpointCatalog(int scopeId) {
        return ENDPOINT_CATALOG.containsKey(scopeId);
    }

    /**
     * Check whether the given scope ID belongs service relation catalog
     *
     * @param scopeId represents an existing scope id.
     * @return true is current scope set {@link ScopeDeclaration#catalog()} == {@link #SERVICE_RELATION_CATALOG_NAME}
     */
    public static boolean inServiceRelationCatalog(int scopeId) {
        return SERVICE_RELATION_CATALOG.containsKey(scopeId);
    }

    /**
     * Check whether the given scope ID belongs service instance relation catalog
     *
     * @param scopeId represents an existing scope id.
     * @return true is current scope set {@link ScopeDeclaration#catalog()} == {@link #SERVICE_INSTANCE_RELATION_CATALOG_NAME}
     */
    public static boolean inServiceInstanceRelationCatalog(int scopeId) {
        return SERVICE_INSTANCE_RELATION_CATALOG.containsKey(scopeId);
    }

    /**
     * Check whether the given scope ID belongs endpoint relation catalog
     *
     * @param scopeId represents an existing scope id.
     * @return true is current scope set {@link ScopeDeclaration#catalog()} == {@link #ENDPOINT_RELATION_CATALOG_NAME}
     */
    public static boolean inEndpointRelationCatalog(int scopeId) {
        return ENDPOINT_RELATION_CATALOG.containsKey(scopeId);
    }

    /**
     * Check whether the given scope ID belongs process catalog
     *
     * @param scopeId represents an existing scope id.
     * @return true is current scope set {@link ScopeDeclaration#catalog()} == {@link #PROCESS_CATALOG_NAME}
     */
    public static boolean inProcessCatalog(int scopeId) {
        return PROCESS_CATALOG.containsKey(scopeId);
    }

    /**
     * Check whether the given scope ID belongs process relation catalog
     *
     * @param scopeId represents an existing scope id.
     * @return true is current scope set {@link ScopeDeclaration#catalog()} == {@link #PROCESS_RELATION_CATALOG_NAME}
     */
    public static boolean inProcessRelationCatalog(int scopeId) {
        return PROCESS_RELATION_CATALOG.containsKey(scopeId);
    }

    /**
     * Get the catalog string name of the given scope
     *
     * @param scope id of the source scope.
     * @return literal string name of the catalog owning the scope. Return `ALL` by default.
     */
    public static String catalogOf(int scope) {
        if (inServiceCatalog(scope)) {
            return SERVICE_CATALOG_NAME;
        }
        if (inServiceInstanceCatalog(scope)) {
            return SERVICE_INSTANCE_CATALOG_NAME;
        }
        if (inEndpointCatalog(scope)) {
            return ENDPOINT_CATALOG_NAME;
        }
        if (inServiceRelationCatalog(scope)) {
            return SERVICE_RELATION_CATALOG_NAME;
        }
        if (inServiceInstanceRelationCatalog(scope)) {
            return SERVICE_INSTANCE_RELATION_CATALOG_NAME;
        }
        if (inEndpointRelationCatalog(scope)) {
            return ENDPOINT_RELATION_CATALOG_NAME;
        }
        if (inProcessCatalog(scope)) {
            return PROCESS_CATALOG_NAME;
        }
        if (inProcessRelationCatalog(scope)) {
            return PROCESS_RELATION_CATALOG_NAME;
        }
        return "ALL";
    }

    /**
     * Get the default columns defined in Scope. All those columns will forward to persistent entity.
     *
     * @param scopeName of the default columns
     */
    public static List<ScopeDefaultColumn> getDefaultColumns(String scopeName) {
        List<ScopeDefaultColumn> scopeDefaultColumns = SCOPE_COLUMNS.get(scopeName);
        if (scopeDefaultColumns == null) {
            throw new UnexpectedException("ScopeDefine name = " + scopeName + " not found.");
        }
        return scopeDefaultColumns;
    }
}
