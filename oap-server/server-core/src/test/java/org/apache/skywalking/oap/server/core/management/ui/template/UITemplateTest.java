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

import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class UITemplateTest {
    @Test
    public void testSerialization() {
        UITemplate uiTemplate = new UITemplate();
        uiTemplate.setTemplateId("id");
        uiTemplate.setConfiguration("configuration");
        uiTemplate.setUpdateTime(1694760289493L);
        uiTemplate.setDisabled(BooleanUtils.FALSE);
        final UITemplate.Builder builder = new UITemplate.Builder();

        final HashMapConverter.ToStorage toStorage = new HashMapConverter.ToStorage();
        builder.entity2Storage(uiTemplate, toStorage);
        final UITemplate uiTemplate2 = builder.storage2Entity(new HashMapConverter.ToEntity(toStorage.obtain()));

        Assertions.assertEquals(uiTemplate, uiTemplate2);

        uiTemplate2.setConfiguration("configuration2");
        // Equals method is only for `templateId` field.
        Assertions.assertEquals(uiTemplate, uiTemplate2);
    }
}
