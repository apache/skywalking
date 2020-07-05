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

package org.apache.skywalking.oap.server.core.storage;

import java.util.List;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;

/**
 * IBatchDAO provides two modes of data persistence supported by most databases, including synchronous and
 * asynchronous.
 */
public interface IBatchDAO extends DAO {
    /**
     * Push data into the database in async mode. This method is driven by streaming process. This method doesn't
     * request the data queryable immediately after the method finished.
     *
     * All data are in the additional mode, no modification.
     *
     * @param insertRequest data to insert.
     */
    void asynchronous(InsertRequest insertRequest);

    /**
     * Make all given PrepareRequest efficient in the sync mode. All requests could be confirmed by the database. All
     * changes are required queryable after method returns.
     *
     * @param prepareRequests data to insert or update. No delete happens in streaming mode.
     */
    void synchronous(List<PrepareRequest> prepareRequests);
}
