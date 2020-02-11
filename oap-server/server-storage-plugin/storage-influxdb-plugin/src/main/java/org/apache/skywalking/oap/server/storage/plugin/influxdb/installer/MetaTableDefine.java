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

package org.apache.skywalking.oap.server.storage.plugin.influxdb.installer;

import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.ENDPOINT_INVENTORY;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.NETWORK_ADDRESS;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.PROFILE_TASK;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.PROFILE_TASK_SEGMENT_SNAPSHOT;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE_INSTANCE_INVENTORY;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE_INVENTORY;

/**
 * Here defines which table is stored in metadata database(H2/MySQL).
 */
public class MetaTableDefine {

    /**
     * Test a {@link Model} is stored in H2/MySQL or not.
     *
     * @param model Model
     * @return true if the {@link Model} is stored in H2/MySQL
     */
    public static boolean contains(Model model) {
        switch (model.getScopeId()) {
            case SERVICE_INVENTORY:
            case SERVICE_INSTANCE_INVENTORY:
            case NETWORK_ADDRESS:
            case ENDPOINT_INVENTORY:
            case PROFILE_TASK:
            case PROFILE_TASK_SEGMENT_SNAPSHOT:
                return true;
        }
        TableMetaInfo.addModel(model);
        return false;
    }
}
