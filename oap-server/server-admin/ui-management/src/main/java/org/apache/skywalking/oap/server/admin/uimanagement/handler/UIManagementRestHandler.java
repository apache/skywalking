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

package org.apache.skywalking.oap.server.admin.uimanagement.handler;

import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Param;
import com.linecorp.armeria.server.annotation.Post;
import com.linecorp.armeria.server.annotation.Put;
import com.linecorp.armeria.server.annotation.RequestObject;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.admin.uimanagement.response.ErrorResponse;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplateManagementService;
import org.apache.skywalking.oap.server.core.query.input.DashboardSetting;
import org.apache.skywalking.oap.server.core.query.type.DashboardConfiguration;
import org.apache.skywalking.oap.server.core.query.type.TemplateChangeStatus;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * REST handler for the UI template admin surface.
 *
 * <p>All routes live on admin-server's HTTP register (default port 17128).
 * Operations forward to the existing {@link UITemplateManagementService}
 * — the storage DAO contract is unchanged. The menu (sidebar navigation)
 * is intentionally NOT served from OAP in 11.0.0+: Horizon UI ships its
 * own menu config in its bundle and computes "layer has services" gating
 * client-side via {@code listServices(layer:...)} against the metadata
 * query surface.
 */
@Slf4j
public class UIManagementRestHandler {

    private final ModuleManager moduleManager;
    private UITemplateManagementService templateService;

    public UIManagementRestHandler(final ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private UITemplateManagementService templates() {
        if (templateService == null) {
            templateService = moduleManager.find(CoreModule.NAME)
                                           .provider()
                                           .getService(UITemplateManagementService.class);
        }
        return templateService;
    }

    /**
     * List all templates. {@code includingDisabled=true} surfaces
     * soft-disabled templates too; default is to skip them.
     */
    @Get("/ui-management/templates")
    public HttpResponse listTemplates(@Param("includingDisabled") final Optional<Boolean> includingDisabled) {
        try {
            final List<DashboardConfiguration> all = templates().getAllTemplates(includingDisabled.orElse(false));
            return HttpResponse.ofJson(all);
        } catch (IOException e) {
            log.error("Failed to list UI templates", e);
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "list_failed", e.getMessage());
        }
    }

    /**
     * Fetch a single template by ID. Returns 404 when the template does not exist.
     */
    @Get("/ui-management/templates/{id}")
    public HttpResponse getTemplate(@Param("id") final String id) {
        if (id == null || id.isEmpty()) {
            return errorResponse(HttpStatus.BAD_REQUEST, "missing_id", "Path parameter 'id' is required.");
        }
        try {
            final DashboardConfiguration template = templates().getTemplate(id);
            if (template == null) {
                return errorResponse(HttpStatus.NOT_FOUND, "not_found",
                                     "No template found with id=" + id);
            }
            return HttpResponse.ofJson(template);
        } catch (IOException e) {
            log.error("Failed to load UI template id={}", id, e);
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "get_failed", e.getMessage());
        }
    }

    /**
     * Add a new template. Body shape:
     * <pre>{ "configuration": "&lt;JSON-encoded config&gt;" }</pre>
     */
    @Post("/ui-management/templates")
    public HttpResponse addTemplate(@RequestObject final DashboardSetting setting) {
        if (setting == null || setting.getId() == null || setting.getId().isEmpty()) {
            return errorResponse(HttpStatus.BAD_REQUEST, "missing_id",
                                 "Request body must include 'id'.");
        }
        if (setting.getConfiguration() == null) {
            return errorResponse(HttpStatus.BAD_REQUEST, "missing_configuration",
                                 "Request body must include 'configuration'.");
        }
        try {
            final TemplateChangeStatus status = templates().addTemplate(setting);
            return statusResponse(status);
        } catch (IOException e) {
            log.error("Failed to add UI template", e);
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "add_failed", e.getMessage());
        }
    }

    /**
     * Update an existing template. Body must include both {@code id} and the
     * new {@code configuration}.
     */
    @Put("/ui-management/templates")
    public HttpResponse changeTemplate(@RequestObject final DashboardSetting setting) {
        if (setting == null || setting.getId() == null || setting.getId().isEmpty()) {
            return errorResponse(HttpStatus.BAD_REQUEST, "missing_id",
                                 "Request body must include 'id'.");
        }
        if (setting.getConfiguration() == null) {
            return errorResponse(HttpStatus.BAD_REQUEST, "missing_configuration",
                                 "Request body must include 'configuration'.");
        }
        try {
            final TemplateChangeStatus status = templates().changeTemplate(setting);
            return statusResponse(status);
        } catch (IOException e) {
            log.error("Failed to update UI template id={}", setting.getId(), e);
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "update_failed", e.getMessage());
        }
    }

    /**
     * Soft-disable a template. Idempotent — disabling an already-disabled
     * template returns success.
     */
    @Post("/ui-management/templates/{id}/disable")
    public HttpResponse disableTemplate(@Param("id") final String id) {
        if (id == null || id.isEmpty()) {
            return errorResponse(HttpStatus.BAD_REQUEST, "missing_id", "Path parameter 'id' is required.");
        }
        try {
            final TemplateChangeStatus status = templates().disableTemplate(id);
            return statusResponse(status);
        } catch (IOException e) {
            log.error("Failed to disable UI template id={}", id, e);
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "disable_failed", e.getMessage());
        }
    }

    private HttpResponse statusResponse(final TemplateChangeStatus status) {
        if (status == null) {
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "no_status",
                                 "Storage layer returned no status.");
        }
        // 200 OK on success, 409 Conflict on a documented service-layer rejection
        // (e.g., template name collision). Callers parse the body either way.
        final HttpStatus http = status.isStatus() ? HttpStatus.OK : HttpStatus.CONFLICT;
        return HttpResponse.ofJson(http, MediaType.JSON_UTF_8, status);
    }

    private HttpResponse errorResponse(final HttpStatus status, final String code, final String message) {
        return HttpResponse.ofJson(status, MediaType.JSON_UTF_8,
                                   ErrorResponse.builder().error(code).message(message).build());
    }
}
