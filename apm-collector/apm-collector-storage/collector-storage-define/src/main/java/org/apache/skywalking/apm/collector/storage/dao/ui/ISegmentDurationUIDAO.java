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

import org.apache.skywalking.apm.collector.storage.base.dao.DAO;
import org.apache.skywalking.apm.collector.storage.ui.trace.TraceBrief;

/**
 * Interface to be implemented for execute database query operation
 * from {@link org.apache.skywalking.apm.collector.storage.table.segment.SegmentDurationTable#TABLE}.
 *
 * @author peng-yongsheng
 * @see org.apache.skywalking.apm.collector.storage.table.segment.SegmentDurationTable
 * @see org.apache.skywalking.apm.collector.storage.StorageModule
 */
public interface ISegmentDurationUIDAO extends DAO {

    /**
     * <p>SQL as: select SEGMENT_ID, START_TIME, SERVICE_NAME, DURATION, IS_ERROR
     * from SEGMENT_DURATION
     * where TIME_BUCKET ge ${startSecondTimeBucket} and TIME_BUCKET le ${endSecondTimeBucket}
     * and DURATION ge ${minDuration} and DURATION le ${maxDuration}
     * and SERVICE_NAME like '%${operationName}%'
     * and SEGMENT_ID in (${segmentIds})
     * and APPLICATION_ID = ${applicationId}
     * LIMIT ${limit} OFFSET ${from}
     *
     * <p>Note: Every conditions maybe not given except limit and from condition.
     *
     * @param startSecondTimeBucket start time format pattern is "yyyyMMddHHmmss"
     * @param endSecondTimeBucket end time format pattern is "yyyyMMddHHmmss"
     * @param minDuration a range condition for query, segment duration greater than given value
     * @param maxDuration a range condition for query, segment duration less than given value
     * @param operationName the entry span's operation name, provide fuzzy query
     * @param applicationId owner id of segment
     * @param limit limits the number of rows returned by the query
     * @param from specified how many rows to skip
     * @param segmentIds mutual exclusion on other condition
     * @return not nullable result list
     */
    TraceBrief loadTop(long startSecondTimeBucket, long endSecondTimeBucket, long minDuration, long maxDuration,
        String operationName, int applicationId, int limit, int from, String... segmentIds);
}
