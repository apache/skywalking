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

package org.apache.skywalking.apm.collector.storage.table.register;

import java.lang.reflect.Field;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.network.trace.component.OfficialComponent;
import org.junit.Test;

/**
 * @author peng-yongsheng
 */
public class ServerTypeDefineTestCase {

    @Test
    public void check() throws IllegalAccessException {
        Field[] fields = ComponentsDefine.class.getDeclaredFields();

        for (Field field : fields) {
            if (field.getType().equals(OfficialComponent.class)) {
                OfficialComponent component = (OfficialComponent)field.get(ComponentsDefine.getInstance());
                ServerTypeDefine.getInstance().getServerTypeId(component.getId());
            }
        }
    }
}
