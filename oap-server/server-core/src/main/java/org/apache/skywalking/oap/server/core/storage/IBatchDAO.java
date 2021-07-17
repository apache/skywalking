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
 * IBatchDAO provides two modes of data persistence supported by most databases, including pure insert and batch hybrid
 * insert/update.
 */
public interface IBatchDAO extends DAO {
    /**
     * Push data into the database in async mode. This method is driven by streaming process. This method doesn't
     * request the data queryable immediately after the method finished.
     *
     * @param insertRequest data to insert.
     */
    void insert(InsertRequest insertRequest);

    /**
     * Push data collection into the database in async mode. This method is driven by streaming process. This method
     * doesn't request the data queryable immediately after the method finished.
     *
     * The method requires thread safe. The OAP core would call this concurrently.
     *
     * @param prepareRequests data to insert or update. No delete happens in streaming mode.
     */
    void flush(List<PrepareRequest> prepareRequests);
}
