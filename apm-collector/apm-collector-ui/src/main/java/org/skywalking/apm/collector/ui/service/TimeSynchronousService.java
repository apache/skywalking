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

package org.skywalking.apm.collector.ui.service;

import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.ui.dao.IInstanceDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class TimeSynchronousService {

    private final Logger logger = LoggerFactory.getLogger(SegmentTopService.class);

    public Long allInstanceLastTime() {
        IInstanceDAO instanceDAO = (IInstanceDAO)DAOContainer.INSTANCE.get(IInstanceDAO.class.getName());
        return instanceDAO.lastHeartBeatTime();
    }

    public Long instanceLastTime(int applicationInstanceId) {
        IInstanceDAO instanceDAO = (IInstanceDAO)DAOContainer.INSTANCE.get(IInstanceDAO.class.getName());
        return instanceDAO.instanceLastHeartBeatTime(applicationInstanceId);
    }
}
