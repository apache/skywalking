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
import org.apache.skywalking.apm.collector.core.util.BooleanUtils;
import org.apache.skywalking.apm.collector.storage.base.dao.DAO;
import org.apache.skywalking.apm.collector.storage.table.register.Instance;
import org.apache.skywalking.apm.collector.storage.ui.application.Application;
import org.apache.skywalking.apm.collector.storage.ui.server.AppServerInfo;

/**
 * Interface to be implemented for execute database query operation
 * from {@link org.apache.skywalking.apm.collector.storage.table.register.InstanceTable#TABLE}.
 *
 * @author peng-yongsheng
 * @see org.apache.skywalking.apm.collector.storage.table.register.InstanceTable
 * @see org.apache.skywalking.apm.collector.storage.StorageModule
 */
public interface IInstanceUIDAO extends DAO {

    /**
     * Returns applications by query operation with given arguments.
     * <p>SQL as: select APPLICATION_ID, count(APPLICATION_ID) from INSTANCE
     * where IS_ADDRESS = {@link BooleanUtils#FALSE} and
     * (( HEARTBEAT_TIME ge ${endSecondTimeBucket} and REGISTER_TIME le ${endSecondTimeBucket})
     * or
     * (REGISTER_TIME le ${endSecondTimeBucket} and HEARTBEAT_TIME ge ${startSecondTimeBucket}))
     * and APPLICATION_ID in (${applicationIds})
     * group by APPLICATION_ID
     * <p>Note: ${applicationIds} may not be given
     *
     * @param startSecondTimeBucket start time format pattern is "yyyyMMddHHmmss"
     * @param endSecondTimeBucket end time format pattern is "yyyyMMddHHmmss"
     * @param applicationIds owner ids of instances
     * @return not nullable result list containing application ids and number of instance.
     */
    List<Application> getApplications(long startSecondTimeBucket, long endSecondTimeBucket, int... applicationIds);

    /**
     * Returns the detail of instance by given instance id.
     * <p>SQL as: select * from INSTANCE where INSTANCE_ID = ${instanceId}
     *
     * @param instanceId argument to bind to the query
     * @return the single instance object, may be null if not exist
     */
    Instance getInstance(int instanceId);

    /**
     * Returns the detail of instances by given arguments.
     * <p>SQL as: select * from INSTANCE
     * where IS_ADDRESS = {@link BooleanUtils#FALSE} and
     * (( HEARTBEAT_TIME ge ${endSecondTimeBucket} and REGISTER_TIME le ${endSecondTimeBucket})
     * or
     * (REGISTER_TIME le ${endSecondTimeBucket} and HEARTBEAT_TIME ge ${startSecondTimeBucket}))
     * and OS_INFO like '%${keyword}%'
     * <p>Note: ${keyword} may not be given
     *
     * @param keyword any words contains in OS information, use fuzzy search
     * @param startSecondTimeBucket start time format pattern is "yyyyMMddHHmmss"
     * @param endSecondTimeBucket end time format pattern is "yyyyMMddHHmmss"
     * @return not nullable result list containing detail of instances.
     */
    List<AppServerInfo> searchServer(String keyword, long startSecondTimeBucket, long endSecondTimeBucket);

    /**
     * Returns the detail of instances by given arguments.
     * <p>SQL as: select * from INSTANCE where
     * where IS_ADDRESS = {@link BooleanUtils#FALSE} and
     * (( HEARTBEAT_TIME ge ${endSecondTimeBucket} and REGISTER_TIME le ${endSecondTimeBucket})
     * or
     * (REGISTER_TIME le ${endSecondTimeBucket} and HEARTBEAT_TIME ge ${startSecondTimeBucket}))
     * and APPLICATION_ID = ${applicationId}
     *
     * @param applicationId owner of instances
     * @param startSecondTimeBucket start time format pattern is "yyyyMMddHHmmss"
     * @param endSecondTimeBucket end time format pattern is "yyyyMMddHHmmss"
     * @return not nullable result list containing detail of instances.
     */
    List<AppServerInfo> getAllServer(int applicationId, long startSecondTimeBucket, long endSecondTimeBucket);

    /**
     * Returns the earliest register time in all instances which
     * belonged to the given application id.
     * <p>SQL as: select REGISTER_TIME from INSTANCE where
     * and APPLICATION_ID = ${applicationId}
     * order by REGISTER_TIME asc
     *
     * @param applicationId owner of instances
     * @return the earliest register time
     */
    long getEarliestRegisterTime(int applicationId);

    /**
     * Returns the latest heart beat time in all instances which
     * belonged to the given application id.
     * <p>SQL as: select HEARTBEAT_TIME from INSTANCE where
     * and APPLICATION_ID = ${applicationId}
     * order by HEARTBEAT_TIME desc
     *
     * @param applicationId owner of instances
     * @return the latest heart beat time
     */
    long getLatestHeartBeatTime(int applicationId);
}
