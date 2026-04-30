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

package org.apache.skywalking.oap.server.receiver.runtimerule.rest;

import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.annotation.Blocking;
import com.linecorp.armeria.server.annotation.Default;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Header;
import com.linecorp.armeria.server.annotation.Param;
import com.linecorp.armeria.server.annotation.Post;
import lombok.Getter;
import org.apache.skywalking.oap.server.core.classloader.Catalog;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.RuntimeRuleClusterClient;
import org.apache.skywalking.oap.server.receiver.runtimerule.reconcile.DSLManager;

/**
 * Armeria-annotated HTTP transport for the runtime-rule admin endpoints. All workflow
 * logic lives behind {@link RuntimeRuleService}; this class only carries route bindings
 * and parameter parsing. The cluster Forward RPC handler reaches the same workflow
 * directly via {@code RuntimeRuleService.execute*} methods — there is no separate
 * forward-target indirection.
 *
 * <p>The {@code catalog} query parameter is the one place untyped {@link String} arrives
 * from the wire. We parse it to {@link Catalog} at the boundary and propagate the typed
 * enum into {@link RuntimeRuleService}; an unknown catalog returns {@code 400
 * invalid_catalog} via {@link RuntimeRuleService#invalidCatalog} without touching any
 * workflow code.
 *
 * <p>Operator and endpoint reference:
 * {@code docs/en/setup/backend/backend-runtime-rule-api.md}.
 */
@Blocking
public class RuntimeRuleRestHandler {

    /** Exposed so the module provider can wire the same {@link RuntimeRuleService}
     *  instance into {@code RuntimeRuleClusterServiceImpl} for cluster-forward dispatch. */
    @Getter
    private final RuntimeRuleService service;

    public RuntimeRuleRestHandler(final ModuleManager moduleManager,
                                  final DSLManager dslManager,
                                  final RuntimeRuleClusterClient clusterClient,
                                  final long forwardRpcDeadlineMs) {
        this.service = new RuntimeRuleService(
            moduleManager, dslManager, clusterClient, forwardRpcDeadlineMs);
    }

    // ---- Canonical routes ----

    @Post("/runtime/rule/addOrUpdate")
    public HttpResponse addOrUpdate(@Param("catalog") final String catalog,
                                    @Param("name") final String name,
                                    @Param("allowStorageChange") @Default("false") final String allowStorageChange,
                                    @Param("force") @Default("false") final String force,
                                    final HttpData body) {
        final Catalog parsed = parseCatalogOrNull(catalog);
        if (parsed == null) {
            return service.invalidCatalog(catalog, name);
        }
        return service.addOrUpdate(parsed, name, allowStorageChange, force, body);
    }

    @Post("/runtime/rule/inactivate")
    public HttpResponse inactivate(@Param("catalog") final String catalog,
                                   @Param("name") final String name) {
        final Catalog parsed = parseCatalogOrNull(catalog);
        if (parsed == null) {
            return service.invalidCatalog(catalog, name);
        }
        return service.inactivate(parsed, name);
    }

    @Post("/runtime/rule/delete")
    public HttpResponse delete(@Param("catalog") final String catalog,
                               @Param("name") final String name,
                               @Param("mode") @Default("") final String mode) {
        final Catalog parsedCatalog = parseCatalogOrNull(catalog);
        if (parsedCatalog == null) {
            return service.invalidCatalog(catalog, name);
        }
        final DeleteMode parsedMode;
        try {
            parsedMode = DeleteMode.of(mode);
        } catch (final IllegalArgumentException badMode) {
            return service.invalidDeleteMode(catalog, name, mode);
        }
        return service.delete(parsedCatalog, name, parsedMode);
    }

    @Get("/runtime/rule/list")
    public HttpResponse list(@Param("catalog") @Default("") final String catalog) {
        // /list's catalog is a filter — empty means "all catalogs". Validation lives inside
        // the service so the empty-string branch is handled in one place.
        return service.list(catalog);
    }

    @Get("/runtime/rule")
    public HttpResponse get(@Param("catalog") final String catalog,
                            @Param("name") final String name,
                            @Param("source") @Default("") final String source,
                            @Header("Accept") @Default("") final String accept,
                            @Header("If-None-Match") @Default("") final String ifNoneMatch) {
        final Catalog parsed = parseCatalogOrNull(catalog);
        if (parsed == null) {
            return service.invalidCatalog(catalog, name);
        }
        return service.get(parsed, name, source, accept, ifNoneMatch);
    }

    @Get("/runtime/rule/bundled")
    public HttpResponse listBundled(@Param("catalog") final String catalog,
                                    @Param("withContent") @Default("true") final String withContent) {
        final Catalog parsed = parseCatalogOrNull(catalog);
        if (parsed == null) {
            return service.invalidCatalog(catalog, null);
        }
        return service.listBundled(parsed, withContent);
    }

    @Get("/runtime/rule/dump")
    public HttpResponse dump() {
        return service.dump();
    }

    @Get("/runtime/rule/dump/{catalog}")
    public HttpResponse dumpCatalog(@Param("catalog") final String catalog) {
        final Catalog parsed = parseCatalogOrNull(catalog);
        if (parsed == null) {
            return service.invalidCatalog(catalog, null);
        }
        return service.dumpCatalog(parsed);
    }

    // ---- Shortcut routes — fixed catalog + name only ----
    //
    // These hard-code the Catalog constant locally so the wire-string-to-enum conversion
    // is compile-time, not request-time.

    @Post("/runtime/mal/otel/addOrUpdate")
    public HttpResponse malOtelAddOrUpdate(@Param("name") final String name,
                                           @Param("allowStorageChange") @Default("false") final String allowStorageChange,
                                           @Param("force") @Default("false") final String force,
                                           final HttpData body) {
        return service.addOrUpdate(Catalog.OTEL_RULES, name, allowStorageChange, force, body);
    }

    @Post("/runtime/mal/otel/inactivate")
    public HttpResponse malOtelInactivate(@Param("name") final String name) {
        return service.inactivate(Catalog.OTEL_RULES, name);
    }

    @Post("/runtime/mal/otel/delete")
    public HttpResponse malOtelDelete(@Param("name") final String name) {
        return service.delete(Catalog.OTEL_RULES, name, DeleteMode.DEFAULT);
    }

    @Post("/runtime/mal/log/addOrUpdate")
    public HttpResponse malLogAddOrUpdate(@Param("name") final String name,
                                          @Param("allowStorageChange") @Default("false") final String allowStorageChange,
                                          @Param("force") @Default("false") final String force,
                                          final HttpData body) {
        return service.addOrUpdate(Catalog.LOG_MAL_RULES, name, allowStorageChange, force, body);
    }

    @Post("/runtime/mal/log/inactivate")
    public HttpResponse malLogInactivate(@Param("name") final String name) {
        return service.inactivate(Catalog.LOG_MAL_RULES, name);
    }

    @Post("/runtime/mal/log/delete")
    public HttpResponse malLogDelete(@Param("name") final String name) {
        return service.delete(Catalog.LOG_MAL_RULES, name, DeleteMode.DEFAULT);
    }

    @Post("/runtime/lal/addOrUpdate")
    public HttpResponse lalAddOrUpdate(@Param("name") final String name,
                                       @Param("allowStorageChange") @Default("false") final String allowStorageChange,
                                       @Param("force") @Default("false") final String force,
                                       final HttpData body) {
        return service.addOrUpdate(Catalog.LAL, name, allowStorageChange, force, body);
    }

    @Post("/runtime/lal/inactivate")
    public HttpResponse lalInactivate(@Param("name") final String name) {
        return service.inactivate(Catalog.LAL, name);
    }

    @Post("/runtime/lal/delete")
    public HttpResponse lalDelete(@Param("name") final String name) {
        return service.delete(Catalog.LAL, name, DeleteMode.DEFAULT);
    }

    /** Parse the catalog query parameter. Returns {@code null} when the value is unknown
     *  so the handler can route through {@link RuntimeRuleService#invalidCatalog} for a
     *  uniform 400 response. */
    private static Catalog parseCatalogOrNull(final String wireValue) {
        if (wireValue == null || wireValue.isEmpty()) {
            return null;
        }
        try {
            return Catalog.of(wireValue);
        } catch (final IllegalArgumentException unknown) {
            return null;
        }
    }
}
