/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.agentregister.worker.instance.dao;

import org.skywalking.apm.collector.storage.define.register.InstanceDataDefine;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;

/**
 * @author pengys5
 */
public class InstanceH2DAO extends H2DAO implements IInstanceDAO {
    @Override public int getInstanceId(int applicationId, String agentUUID) {
        return 0;
    }

    @Override public int getMaxInstanceId() {
        return 0;
    }

    @Override public int getMinInstanceId() {
        return 0;
    }

    @Override public void save(InstanceDataDefine.Instance instance) {

    }

    @Override public void updateHeartbeatTime(int instanceId, long heartbeatTime) {

    }

    @Override public int getApplicationId(int applicationInstanceId) {
        return 0;
    }
}
