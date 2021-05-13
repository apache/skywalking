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

import org.apache.skywalking.oap.server.core.query.enumeration.TemplateType;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.junit.Assert;
import org.junit.Test;

public class UITemplateTest {
    @Test
    public void testSerialization() {
        UITemplate uiTemplate = new UITemplate();
        uiTemplate.setName("name");
        uiTemplate.setConfiguration("configuration");
        uiTemplate.setType(TemplateType.DASHBOARD.name());
        uiTemplate.setActivated(BooleanUtils.TRUE);
        uiTemplate.setDisabled(BooleanUtils.FALSE);

        final UITemplate.Builder builder = new UITemplate.Builder();
        final UITemplate uiTemplate2 = builder.storage2Entity(builder.entity2Storage(uiTemplate));

        Assert.assertEquals(uiTemplate, uiTemplate2);

        uiTemplate2.setConfiguration("configuration2");
        uiTemplate.setType(TemplateType.TOPOLOGY_ENDPOINT.name());
        uiTemplate.setActivated(BooleanUtils.FALSE);
        uiTemplate.setDisabled(BooleanUtils.TRUE);
        // Equals method is only for `name` field.
        Assert.assertEquals(uiTemplate, uiTemplate2);
    }
}
