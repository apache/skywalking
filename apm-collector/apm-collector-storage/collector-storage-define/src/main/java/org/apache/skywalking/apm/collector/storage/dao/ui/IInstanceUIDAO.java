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
import org.apache.skywalking.apm.collector.storage.table.register.Instance;
import org.apache.skywalking.apm.collector.storage.ui.application.Application;
import org.apache.skywalking.apm.collector.storage.ui.server.AppServerInfo;

/**
 * @author peng-yongsheng
 */
public interface IInstanceUIDAO extends DAO {
    Long lastHeartBeatTime();

    Long instanceLastHeartBeatTime(long applicationInstanceId);

    List<Application> getApplications(long startSecondTimeBucket, long endSecondTimeBucket, int... applicationIds);

    Instance getInstance(int instanceId);

    List<AppServerInfo> searchServer(String keyword, long startSecondTimeBucket, long endSecondTimeBucket);

    List<AppServerInfo> getAllServer(int applicationId, long startSecondTimeBucket, long endSecondTimeBucket);

    long getEarliestRegisterTime(int applicationId);

    long getLatestHeartBeatTime(int applicationId);
}
