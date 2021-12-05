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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer;

import com.google.common.collect.ImmutableList;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplate;
import org.apache.skywalking.oap.server.core.query.enumeration.TemplateType;
import org.apache.skywalking.oap.server.core.query.type.DashboardConfiguration;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;

import java.util.List;

public class DashboardConfigurationDeserializer extends AbstractBanyanDBDeserializer<DashboardConfiguration> {
    public DashboardConfigurationDeserializer() {
        super(UITemplate.INDEX_NAME,
                ImmutableList.of(UITemplate.NAME, UITemplate.DISABLED),
                ImmutableList.of(UITemplate.ACTIVATED, UITemplate.CONFIGURATION, UITemplate.TYPE));
    }

    @Override
    public DashboardConfiguration map(RowEntity row) {
        DashboardConfiguration dashboardConfiguration = new DashboardConfiguration();
        final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
        // name
        dashboardConfiguration.setName((String) searchable.get(0).getValue());
        // disabled
        dashboardConfiguration.setDisabled(BooleanUtils.valueToBoolean(((Number) searchable.get(1).getValue()).intValue()));
        final List<TagAndValue<?>> data = row.getTagFamilies().get(1);
        // activated
        dashboardConfiguration.setActivated(BooleanUtils.valueToBoolean(((Number) data.get(0).getValue()).intValue()));
        // configuration
        dashboardConfiguration.setConfiguration((String) data.get(1).getValue());
        // type
        dashboardConfiguration.setType(TemplateType.forName((String) data.get(2).getValue()));
        return dashboardConfiguration;
    }
}
