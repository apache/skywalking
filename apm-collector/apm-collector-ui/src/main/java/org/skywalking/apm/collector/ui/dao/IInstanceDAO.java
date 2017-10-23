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

package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;
import java.util.List;
import org.skywalking.apm.collector.storage.define.register.InstanceDataDefine;

/**
 * @author peng-yongsheng
 */
public interface IInstanceDAO {
    Long lastHeartBeatTime();

    Long instanceLastHeartBeatTime(long applicationInstanceId);

    JsonArray getApplications(long startTime, long endTime);

    InstanceDataDefine.Instance getInstance(int instanceId);

    List<InstanceDataDefine.Instance> getInstances(int applicationId, long timeBucket);

    class Application {
        private final int applicationId;
        private final long count;

        public Application(int applicationId, long count) {
            this.applicationId = applicationId;
            this.count = count;
        }

        public int getApplicationId() {
            return applicationId;
        }

        public long getCount() {
            return count;
        }
    }
}
