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

import java.io.Reader;
import java.lang.reflect.Field;
import java.util.Map;
import org.apache.commons.collections.map.CaseInsensitiveMap;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.network.trace.component.OfficialComponent;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Guarantee that the components defined in {@link ComponentsDefine} are in sync with those in file {@code
 * component-libraries.yml}, note that this test only verifies Java components.
 */
@SuppressWarnings("rawtypes")
public class ComponentLibrariesTest {
    @Test
    public void testComponentsAreInSync() throws Exception {
        final Reader reader = ResourceUtils.read("component-libraries.yml");
        final Map map = new Yaml().loadAs(reader, Map.class);
        final CaseInsensitiveMap caseInsensitiveMap = new CaseInsensitiveMap(map);
        for (final Field field : ComponentsDefine.class.getFields()) {
            final OfficialComponent component = (OfficialComponent) field.get(null);
            final String normalizedComponentName = component.getName().replaceAll("\\.", "");
            if (!caseInsensitiveMap.containsKey(normalizedComponentName)) {
                fail("Component " + component.getName() + " is not registered in component-libraries.yml");
            }
            final Map componentInMap = (Map) caseInsensitiveMap.get(normalizedComponentName);
            final int id = (Integer) componentInMap.get("id");
            assertEquals(
                "Component id defined in class ComponentsDefine should be the same as that in component-libraries.yml",
                id, component.getId()
            );
        }
    }
}
