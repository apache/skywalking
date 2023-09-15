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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.management.ManagementData;
import org.apache.skywalking.oap.server.core.analysis.worker.ManagementStreamProcessor;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.UI_TEMPLATE;

@Setter
@Getter
@ScopeDeclaration(id = UI_TEMPLATE, name = "UITemplate")
@Stream(name = UITemplate.INDEX_NAME, scopeId = UI_TEMPLATE, builder = UITemplate.Builder.class, processor = ManagementStreamProcessor.class)
@EqualsAndHashCode(of = {
    "templateId"
}, callSuper = false)
public class UITemplate extends ManagementData {
    public static final String INDEX_NAME = "ui_template";
    public static final String TEMPLATE_ID = "template_id";
    public static final String CONFIGURATION = "configuration";
    public static final String UPDATE_TIME = "update_time";
    public static final String DISABLED = "disabled";

    @Column(name = TEMPLATE_ID)
    private String templateId;
    /**
     * Configuration in JSON format.
     */
    @Column(name = CONFIGURATION, storageOnly = true, length = 1_000_000)
    private String configuration;
    @Column(name = UPDATE_TIME)
    private Long updateTime;
    @Column(name = DISABLED)
    private Integer disabled;

    @Override
    public StorageID id() {
        return new StorageID().append(TEMPLATE_ID, templateId);
    }

    public static class Builder implements StorageBuilder<UITemplate> {
        @Override
        public UITemplate storage2Entity(final Convert2Entity converter) {
            UITemplate uiTemplate = new UITemplate();
            uiTemplate.setTemplateId((String) converter.get(TEMPLATE_ID));
            uiTemplate.setConfiguration((String) converter.get(CONFIGURATION));
            uiTemplate.setUpdateTime(((Number) converter.get(UPDATE_TIME)).longValue());
            uiTemplate.setDisabled(((Number) converter.get(DISABLED)).intValue());
            return uiTemplate;
        }

        @Override
        public void entity2Storage(final UITemplate storageData, final Convert2Storage converter) {
            converter.accept(TEMPLATE_ID, storageData.getTemplateId());
            converter.accept(CONFIGURATION, storageData.getConfiguration());
            converter.accept(UPDATE_TIME, storageData.getUpdateTime());
            converter.accept(DISABLED, storageData.getDisabled());
        }
    }
}
