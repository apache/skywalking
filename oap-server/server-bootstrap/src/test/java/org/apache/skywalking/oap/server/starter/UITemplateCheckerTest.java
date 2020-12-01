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

package org.apache.skywalking.oap.server.starter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplate;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplateInitializer;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * @since 8.2.0 SkyWalking supports multiple UI initialized templates, this check is avoiding the duplicated definitions
 * in the multiple files.
 *
 * If the codes include duplicate template name in same or different template files in `ui-initialized-templates`
 * folder, this test case would fail, in order to block the merge.
 */
public class UITemplateCheckerTest {
    @Test
    public void testNoTemplateConflict() throws FileNotFoundException {
        final File[] templateFiles = ResourceUtils.getPathFiles("ui-initialized-templates");
        final List<UITemplate> uiTemplates = new ArrayList<>();
        for (final File templateFile : templateFiles) {
            UITemplateInitializer initializer = new UITemplateInitializer(
                new FileInputStream(templateFile));
            uiTemplates.addAll(initializer.read());
        }

        final List<UITemplate> distinct = uiTemplates.stream().distinct().collect(Collectors.toList());
        Assert.assertEquals(distinct.size(), uiTemplates.size());
    }
}
