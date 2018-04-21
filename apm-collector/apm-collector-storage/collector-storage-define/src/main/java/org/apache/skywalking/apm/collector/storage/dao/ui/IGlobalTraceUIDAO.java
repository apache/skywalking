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

/**
 * Interface to be implemented for execute database query operation
 * from {@link org.apache.skywalking.apm.collector.storage.table.global.GlobalTraceTable#TABLE}.
 *
 * @author peng-yongsheng
 * @see org.apache.skywalking.apm.collector.storage.table.global.GlobalTraceTable
 * @see org.apache.skywalking.apm.collector.storage.StorageModule
 */
public interface IGlobalTraceUIDAO extends DAO {

    /**
     * Returns global trace ids by query operation with given segment id.
     * Generally, only one global trace id will be found, but found more
     * than one id when given segment id been in a batch process.
     * <p>SQL as: select TRACE_ID from GLOBAL_TRACE where SEGMENT_ID = ${segmentId},
     *
     * @param segmentId argument to bind to the query
     * @return not nullable result list containing global trace ids.
     */
    List<String> getGlobalTraceId(String segmentId);

    /**
     * Returns segment ids by query operation with given global trace id.
     * <p>SQL as: select SEGMENT_ID from GLOBAL_TRACE where TRACE_ID = ${globalTraceId},
     *
     * @param globalTraceId argument to bind to the query
     * @return not nullable result list containing segment ids.
     */
    List<String> getSegmentIds(String globalTraceId);
}
