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

package org.apache.skywalking.oap.server.storage.plugin.iotdb.management;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.FunctionCategory;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplate;
import org.apache.skywalking.oap.server.core.query.enumeration.TemplateType;
import org.apache.skywalking.oap.server.core.query.input.DashboardSetting;
import org.apache.skywalking.oap.server.core.query.type.DashboardConfiguration;
import org.apache.skywalking.oap.server.core.storage.model.ExtraQueryIndex;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBStorageConfig;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBTableMetaInfo;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClientTest.retrieval;
import static org.assertj.core.api.Assertions.assertThat;

public class IoTDBUITemplateManagementDAOTest {
    private IoTDBUITemplateManagementDAO uiTemplateManagementDAO;

    @Rule
    public GenericContainer iotdb = new GenericContainer(DockerImageName.parse("apache/iotdb:0.12.2-node")).withExposedPorts(6667);

    @Before
    public void setUp() throws Exception {
        IoTDBStorageConfig config = new IoTDBStorageConfig();
        config.setHost(iotdb.getHost());
        config.setRpcPort(iotdb.getFirstMappedPort());
        config.setUsername("root");
        config.setPassword("root");
        config.setStorageGroup("root.skywalking");
        config.setSessionPoolSize(3);
        config.setFetchTaskLogMaxSize(1000);

        IoTDBClient client = new IoTDBClient(config);
        client.connect();

        uiTemplateManagementDAO = new IoTDBUITemplateManagementDAO(client);

        int scopeId = 2;
        boolean record = true;
        boolean superDataset = true;
        boolean timeRelativeID = false;
        List<ModelColumn> modelColumns = new ArrayList<>();
        List<ExtraQueryIndex> extraQueryIndices = new ArrayList<>();
        retrieval(UITemplate.class, UITemplate.INDEX_NAME, modelColumns, extraQueryIndices, scopeId);
        Model uiTemplateModel = new Model(
                UITemplate.INDEX_NAME, modelColumns, extraQueryIndices, scopeId, DownSampling.Second, record,
                superDataset, FunctionCategory.uniqueFunctionName(InstanceTraffic.class), timeRelativeID);
        IoTDBTableMetaInfo.addModel(uiTemplateModel);
    }

    @Test
    public void getAllTemplates() throws IOException {
        List<DashboardConfiguration> dashboardConfigurationList = uiTemplateManagementDAO.getAllTemplates(false);
        dashboardConfigurationList.forEach(dashboardConfiguration -> {
            assertThat(dashboardConfiguration.isDisabled()).isFalse();
        });
    }

    @Test
    public void addTemplate() throws IOException {
        DashboardSetting setting = new DashboardSetting();
        setting.setName("dashboard_setting1");
        setting.setType(TemplateType.DASHBOARD);
        setting.setConfiguration("{conf: add_1}");
        setting.setActive(false);
        uiTemplateManagementDAO.addTemplate(setting);

        setting = new DashboardSetting();
        setting.setName("dashboard_setting1");
        setting.setType(TemplateType.DASHBOARD);
        setting.setConfiguration("{conf: add_2}");
        setting.setActive(false);
        uiTemplateManagementDAO.addTemplate(setting);
    }

    @Test
    public void changeTemplate() throws IOException {
        DashboardSetting setting = new DashboardSetting();
        setting.setName("dashboard_setting1");
        setting.setType(TemplateType.DASHBOARD);
        setting.setConfiguration("{conf: change_2}");
        setting.setActive(false);
        uiTemplateManagementDAO.changeTemplate(setting);
    }

    @Test
    public void disableTemplate() throws IOException {
        uiTemplateManagementDAO.disableTemplate("dashboard_setting1");
    }
}