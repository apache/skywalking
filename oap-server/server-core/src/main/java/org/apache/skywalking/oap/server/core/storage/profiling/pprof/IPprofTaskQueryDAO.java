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

package org.apache.skywalking.oap.server.core.storage.profiling.pprof;

import java.io.IOException;
import java.util.List;
import org.apache.skywalking.oap.server.core.query.type.PprofTask;
import org.apache.skywalking.oap.server.core.storage.DAO;

public interface IPprofTaskQueryDAO extends DAO {

    /**
     * search task list in appoint time bucket
     *
     * @param serviceId       monitor service id, maybe null
     * @param startTimeBucket time bucket bigger than or equals, nullable
     * @param endTimeBucket   time bucket smaller than or equals, nullable
     * @param limit           limit count, if null means query all
     */
    List<PprofTask> getTaskList(final String serviceId, final Long startTimeBucket,
                                final Long endTimeBucket, final Integer limit) throws IOException;

    /**
     * query profile task by id
     *
     * @param id taskId
     * @return task data
     */
    PprofTask getById(final String id) throws IOException;

}
