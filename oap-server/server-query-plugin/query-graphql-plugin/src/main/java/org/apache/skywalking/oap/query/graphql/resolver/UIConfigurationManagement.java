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

package org.apache.skywalking.oap.query.graphql.resolver;

import com.coxautodev.graphql.tools.GraphQLMutationResolver;
import com.coxautodev.graphql.tools.GraphQLQueryResolver;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplateManagementService;
import org.apache.skywalking.oap.server.core.query.input.DashboardSetting;
import org.apache.skywalking.oap.server.core.query.type.DashboardConfiguration;
import org.apache.skywalking.oap.server.core.query.type.TemplateChangeStatus;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * UI Configuration including dashboard, topology pop up page, is based on the available templates or manual configuration.
 * UIConfigurationManagement provides the query and operations of templates.
 *
 * @since 8.0.0
 */
@RequiredArgsConstructor
public class UIConfigurationManagement implements GraphQLQueryResolver, GraphQLMutationResolver {
    private final ModuleManager manager;
    private UITemplateManagementService uiTemplateManagementService;

    private UITemplateManagementService getUITemplateManagementService() {
        if (uiTemplateManagementService == null) {
            this.uiTemplateManagementService = manager.find(CoreModule.NAME)
                    .provider()
                    .getService(UITemplateManagementService.class);
        }
        return uiTemplateManagementService;
    }

    public List<DashboardConfiguration> getAllTemplates(Boolean includingDisabled) throws IOException {
        if (includingDisabled == null) {
            includingDisabled = false;
        }
        return getUITemplateManagementService().getAllTemplates(includingDisabled);
    }

    public TemplateChangeStatus addTemplate(DashboardSetting setting) throws IOException {
        return getUITemplateManagementService().addTemplate(setting);
    }

    public TemplateChangeStatus changeTemplate(DashboardSetting setting) throws IOException {
        return getUITemplateManagementService().changeTemplate(setting);
    }

    public TemplateChangeStatus disableTemplate(String name) throws IOException {
        return getUITemplateManagementService().disableTemplate(name);
    }
}
