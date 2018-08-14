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
 * Interface to be implemented for execute database query operation
 * from {@link org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarmListTable#TABLE}.
 *
 * @author peng-yongsheng
 * @see org.apache.skywalking.apm.collector.storage.StorageModule
 */
public interface IApplicationAlarmListUIDAO extends DAO {

    /**
     * <p>SQL as: select TIME_BUCKET, APPLICATION_ID, count(APPLICATION_ID) from APPLICATION_ALARM_LIST
     * where TIME_BUCKET ge ${startTimeBucket} and TIME_BUCKET le ${endTimeBucket}
     * group by TIME_BUCKET, APPLICATION_ID
     *
     * <p>Use {@link org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder#build(Step, String)}
     * to generate table name which mixed with step name.
     *
     * @param step the step which represent time formats
     * @param startTimeBucket start time bucket
     * @param endTimeBucket end time bucket
     * @return not nullable result list
     */
    List<AlarmTrend> getAlarmedApplicationNum(Step step, long startTimeBucket, long endTimeBucket);

    class AlarmTrend {
        private long timeBucket;
        private int numberOfApplication;

        public long getTimeBucket() {
            return timeBucket;
        }

        public void setTimeBucket(long timeBucket) {
            this.timeBucket = timeBucket;
        }

        public int getNumberOfApplication() {
            return numberOfApplication;
        }

        public void setNumberOfApplication(int numberOfApplication) {
            this.numberOfApplication = numberOfApplication;
        }
    }
}
