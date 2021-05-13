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

package org.apache.skywalking.oap.server.core.management.ui.template;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.query.input.DashboardSetting;
import org.apache.skywalking.oap.server.core.query.type.DashboardConfiguration;
import org.apache.skywalking.oap.server.core.query.type.TemplateChangeStatus;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.management.UITemplateManagementDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;

@RequiredArgsConstructor
public class UITemplateManagementService implements Service {
    private final ModuleManager moduleManager;
    private UITemplateManagementDAO uiTemplateManagementDAO;

    private UITemplateManagementDAO getUITemplateManagementDAO() {
        if (uiTemplateManagementDAO == null) {
            this.uiTemplateManagementDAO = moduleManager.find(StorageModule.NAME)
                                                        .provider()
                                                        .getService(UITemplateManagementDAO.class);
        }
        return uiTemplateManagementDAO;
    }

    public List<DashboardConfiguration> getAllTemplates(Boolean includingDisabled) throws IOException {
        final List<DashboardConfiguration> allTemplates =
            getUITemplateManagementDAO().getAllTemplates(includingDisabled);
        // Make sure the template in A-Za-z
        Collections.sort(allTemplates, Comparator.comparing(DashboardConfiguration::getName));
        return allTemplates;
    }

    public TemplateChangeStatus addTemplate(DashboardSetting setting) throws IOException {
        return getUITemplateManagementDAO().addTemplate(setting);
    }

    public TemplateChangeStatus changeTemplate(DashboardSetting setting) throws IOException {
        return getUITemplateManagementDAO().changeTemplate(setting);
    }

    public TemplateChangeStatus disableTemplate(String name) throws IOException {
        return getUITemplateManagementDAO().disableTemplate(name);
    }
}
