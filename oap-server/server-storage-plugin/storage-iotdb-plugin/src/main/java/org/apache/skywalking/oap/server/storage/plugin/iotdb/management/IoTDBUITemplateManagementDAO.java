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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplate;
import org.apache.skywalking.oap.server.core.query.input.DashboardSetting;
import org.apache.skywalking.oap.server.core.query.type.DashboardConfiguration;
import org.apache.skywalking.oap.server.core.query.type.TemplateChangeStatus;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.management.UITemplateManagementDAO;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBIndexes;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.base.IoTDBInsertRequest;

@Slf4j
@RequiredArgsConstructor
public class IoTDBUITemplateManagementDAO implements UITemplateManagementDAO {
    private final IoTDBClient client;
    private final StorageBuilder<UITemplate> storageBuilder = new UITemplate.Builder();
    private static final long UI_TEMPLATE_TIMESTAMP = 1L;

    @Override
    public DashboardConfiguration getTemplate(final String id) throws IOException {
        if (StringUtil.isEmpty(id)) {
            return null;
        }
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, UITemplate.INDEX_NAME);
        Map<String, String> indexAndValueMap = new HashMap<>();
        indexAndValueMap.put(IoTDBIndexes.ID_IDX, id);
        query = client.addQueryIndexValue(UITemplate.INDEX_NAME, query, indexAndValueMap);
        query.append(" limit 1").append(IoTDBClient.ALIGN_BY_DEVICE);

        List<? super StorageData> storageDataList = client.filterQuery(
            UITemplate.INDEX_NAME, query.toString(), storageBuilder);
        if (storageDataList.size() > 0) {
            return new DashboardConfiguration().fromEntity((UITemplate) storageDataList.get(0));
        }
        return null;
    }

    @Override
    public List<DashboardConfiguration> getAllTemplates(Boolean includingDisabled) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, UITemplate.INDEX_NAME);
        query = client.addQueryAsterisk(UITemplate.INDEX_NAME, query);
        if (!includingDisabled) {
            query.append(" where ").append(UITemplate.DISABLED).append(" = ").append(BooleanUtils.FALSE);
        }
        query.append(IoTDBClient.ALIGN_BY_DEVICE);

        List<? super StorageData> storageDataList = client.filterQuery(UITemplate.INDEX_NAME, query.toString(),
                                                                       storageBuilder
        );
        List<DashboardConfiguration> dashboardConfigurationList = new ArrayList<>(storageDataList.size());
        storageDataList.forEach(storageData ->
                                    dashboardConfigurationList.add(
                                        new DashboardConfiguration().fromEntity((UITemplate) storageData)));
        return dashboardConfigurationList;
    }

    @Override
    public TemplateChangeStatus addTemplate(DashboardSetting setting) throws IOException {
        final UITemplate uiTemplate = setting.toEntity();

        IoTDBInsertRequest request = new IoTDBInsertRequest(UITemplate.INDEX_NAME, UI_TEMPLATE_TIMESTAMP,
                                                            uiTemplate, storageBuilder
        );
        client.write(request);
        return TemplateChangeStatus.builder().status(true).id(setting.getId()).build();
    }

    @Override
    public TemplateChangeStatus changeTemplate(DashboardSetting setting) throws IOException {
        final UITemplate uiTemplate = setting.toEntity();

        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, UITemplate.INDEX_NAME);
        query.append(IoTDBClient.DOT).append(client.indexValue2LayerName(uiTemplate.id()))
             .append(IoTDBClient.ALIGN_BY_DEVICE);
        List<? super StorageData> queryResult = client.filterQuery(
            UITemplate.INDEX_NAME, query.toString(), storageBuilder);
        if (queryResult.size() == 0) {
            return TemplateChangeStatus.builder()
                                       .status(false)
                                       .id(setting.getId())
                                       .message("Can't find the template")
                                       .build();
        } else {
            IoTDBInsertRequest request = new IoTDBInsertRequest(UITemplate.INDEX_NAME, UI_TEMPLATE_TIMESTAMP,
                                                                uiTemplate, storageBuilder
            );
            client.write(request);
            return TemplateChangeStatus.builder().status(true).id(setting.getId()).build();
        }
    }

    @Override
    public TemplateChangeStatus disableTemplate(String id) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, UITemplate.INDEX_NAME);
        query.append(IoTDBClient.DOT).append(client.indexValue2LayerName(id))
             .append(IoTDBClient.ALIGN_BY_DEVICE);

        List<? super StorageData> queryResult = client.filterQuery(
            UITemplate.INDEX_NAME, query.toString(), storageBuilder);
        if (queryResult.size() == 0) {
            return TemplateChangeStatus.builder().status(false).id(id).message("Can't find the template").build();
        } else {
            final UITemplate uiTemplate = (UITemplate) queryResult.get(0);
            uiTemplate.setDisabled(BooleanUtils.TRUE);
            IoTDBInsertRequest request = new IoTDBInsertRequest(UITemplate.INDEX_NAME, UI_TEMPLATE_TIMESTAMP,
                                                                uiTemplate, storageBuilder
            );
            client.write(request);
            return TemplateChangeStatus.builder().status(true).id(id).build();
        }
    }
}
