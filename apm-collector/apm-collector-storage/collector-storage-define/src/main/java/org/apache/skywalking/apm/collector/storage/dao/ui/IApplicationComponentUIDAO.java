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

package org.apache.skywalking.apm.collector.storage.dao.ui;

import java.util.List;
import org.apache.skywalking.apm.collector.storage.base.dao.DAO;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;

/**
 * @author peng-yongsheng
 */
public interface IApplicationComponentUIDAO extends DAO {

    List<ApplicationComponent> load(Step step, long startTimeBucket, long endTimeBucket);

    class ApplicationComponent {
        private int componentId;
        private int applicationId;

        public int getComponentId() {
            return componentId;
        }

        public void setComponentId(int componentId) {
            this.componentId = componentId;
        }

        public int getApplicationId() {
            return applicationId;
        }

        public void setApplicationId(int applicationId) {
            this.applicationId = applicationId;
        }
    }
}
